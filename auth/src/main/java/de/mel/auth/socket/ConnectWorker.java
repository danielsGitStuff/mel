package de.mel.auth.socket;

import de.mel.Lok;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.jobs.AConnectJob;
import de.mel.auth.jobs.ConnectJob;
import de.mel.auth.jobs.IsolatedConnectJob;
import de.mel.auth.jobs.Job;
import de.mel.auth.service.MelAuthServiceImpl;
import de.mel.auth.service.MelWorker;
import de.mel.auth.socket.process.imprt.MelCertRetriever;
import de.mel.auth.socket.process.transfer.MelIsolatedProcess;
import de.mel.auth.tools.CountdownLock;
import de.mel.auth.tools.N;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.sql.SqlQueriesException;

import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URISyntaxException;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Objects;

/**
 * Connects to other instances on the network.
 */
@SuppressWarnings("Duplicates")
public class ConnectWorker extends MelWorker {

    private final AConnectJob connectJob;
    private final MelAuthServiceImpl melAuthService;
    private MelAuthSocket melAuthSocket;

    public ConnectWorker(MelAuthServiceImpl melAuthService, AConnectJob connectJob) {
        Objects.requireNonNull(connectJob);
        this.melAuthService = melAuthService;
        this.connectJob = connectJob;
        connectJob.always((state, resolved, rejected) -> super.shutDown());
        addJob(connectJob);
    }

    public ConnectWorker(MelAuthServiceImpl melAuthService, MelAuthSocket melAuthSocket) {
        this(melAuthService, melAuthSocket.getConnectJob());
        this.melAuthSocket = melAuthSocket;
    }


    @Override
    public String getRunnableName() {
        String line = "Connecting to: " + connectJob.getAddress() + ":" + connectJob.getPort() + "/" + connectJob.getPortCert();
        return line;
    }


    private <T extends MelIsolatedProcess> DeferredObject<T, Exception, Void> isolate(IsolatedConnectJob<T> originalJob) {
        N runner = new N(e -> {
            e.printStackTrace();
            originalJob.reject(e);
        });
        runner.runTry(() -> {
            melAuthSocket = new MelAuthSocket(melAuthService, originalJob);
            melAuthSocket.setRunnableName("-> " + originalJob.getAddress() + ":" + connectJob.getPort());
            MelAuthProcess melAuthProcess = new MelAuthProcess(melAuthSocket);
            melAuthProcess.authenticate(originalJob);
        });
        return originalJob;
    }

    private DeferredObject<MelValidationProcess, Exception, Void> auth(AConnectJob originalJob) {
        ConnectJob dummyJob = new ConnectJob(originalJob.getCertificateId(), originalJob.getAddress(), originalJob.getPort(), originalJob.getPortCert(), false);
//        DeferredObject<MelValidationProcess, Exception, Void> deferred = dummyJob;
        N runner = new N(e -> {
            e.printStackTrace();
            dummyJob.reject(e);
        });
        runner.runTry(() -> {
            if (melAuthSocket == null)
                melAuthSocket = new MelAuthSocket(melAuthService, dummyJob);
            melAuthSocket.setRunnableName("-> " + originalJob.getAddress() + ":" + connectJob.getPort());
//            Socket socket = melAuthService.getCertificateManager().createSocket();
//            socket.connect(new InetSocketAddress(job.getAddress(), job.getPort()));
            MelAuthProcess melAuthProcess = new MelAuthProcess(melAuthSocket);
            melAuthProcess.authenticate(dummyJob);
        });
        return dummyJob;
    }

    /**
     * connects and blocks
     *
     * @param job
     * @return
     */
    void connect(AConnectJob job) {
        final CountdownLock lock = new CountdownLock(1);
        final Long remoteCertId = job.getCertificateId();
        final String address = job.getAddress();
        final Integer port = job.getPort();
        final Integer portCert = job.getPortCert();
        final boolean regOnUnknown = N.result(() -> {
            if (job instanceof ConnectJob)
                return ((ConnectJob) job).getRegOnUnknown();
            return false;
        });

//        Lok.debug("MelAuthSocket.connect(id=" + remoteCertId + " addr=" + address + " port=" + port + " portCert=" + portCert + " reg=" + regOnUnknown + ")");
        melAuthService.getPowerManager().wakeLock(this);
        if (job instanceof ConnectJob) {
            DeferredObject result = job;
            N runner = new N(e -> {
                if (result.isPending())
                    result.reject(e);
                melAuthService.getPowerManager().releaseWakeLock(this);
                stopConnecting();
                lock.unlock();
            });
            DeferredObject<MelValidationProcess, Exception, Void> firstAuth = this.auth(job);
            firstAuth.done(result1 -> {
                result.resolve(result1);
                melAuthService.getPowerManager().releaseWakeLock(this);
            }).fail(except -> runner.runTry(() -> {
                if (except instanceof ShamefulSelfConnectException) {
                    result.reject(except);
                    melAuthService.getPowerManager().releaseWakeLock(this);
                    shutDown();
                } else if (except instanceof ConnectException) {
                    Lok.error(getClass().getSimpleName() + " for " + melAuthService.getName() + ".connect.HOST:NOT:REACHABLE");
                    result.reject(except);
                    melAuthService.getPowerManager().releaseWakeLock(this);
                    stopConnecting();
                } else if (regOnUnknown && remoteCertId == null) {
                    // try to register
                    DeferredObject<Certificate, Exception, Object> importPromise = new DeferredObject<>();
                    DeferredObject<Certificate, Exception, Void> registered = new DeferredObject<>();
                    this.importCertificate(importPromise, address, port, portCert);
                    importPromise.done(importedCert -> {
                        runner.runTry(() -> {
                            job.setCertificateId(importedCert.getId().v());
                            this.register(registered, importedCert, address, port);
                            registered.done(registeredCert -> {
                                runner.runTry(() -> {
                                    //connection is no more -> need new socket
                                    // create a new job that is not allowed to register.
                                    ConnectJob secondJob = new ConnectJob(connectJob.getCertificateId(), connectJob.getAddress(), connectJob.getPort(), connectJob.getPortCert(), false);
                                    secondJob.done(result1 -> {
                                        result.resolve(result1);
                                        melAuthService.getPowerManager().releaseWakeLock(this);
                                        shutDown();
                                    }).fail(result1 -> {
                                        result.reject(result1);
                                        melAuthService.getPowerManager().releaseWakeLock(this);
                                        shutDown();
                                    });
                                    this.addJob(secondJob);
                                });

                            }).fail(exception -> {
                                        // it won't compile otherwise. don't know why.
                                        // compiler thinks exception is an Object instead of Exception
                                        ((Exception) exception).printStackTrace();
                                        result.reject(exception);
                                        melAuthService.getPowerManager().releaseWakeLock(this);
                                        stopConnecting();
                                    }
                            );
                        });
                    }).fail(ee -> {
                        ee.printStackTrace();
                        result.reject(ee);
                        melAuthService.getPowerManager().releaseWakeLock(this);
                        stopConnecting();
                    });
                } else {
                    if (!(except instanceof ShamefulSelfConnectException)) {
                        result.reject(new CannotConnectException(except, address, port));
                    } else {
                        result.reject(except);
                    }
                    melAuthService.getPowerManager().releaseWakeLock(this);
                    stopConnecting();
                }
            }));
        } else if (job instanceof IsolatedConnectJob) {
            this.isolate((IsolatedConnectJob<? extends MelIsolatedProcess>) job)
                    .fail(result -> {
                        stopConnecting();
                        melAuthService.getPowerManager().releaseWakeLock(this);
                    })
                    .done(result -> melAuthService.getPowerManager().releaseWakeLock(this));
        }
    }

    private void stopConnecting() {
        shutDown();
    }

    public void importCertificate(DeferredObject<de.mel.auth.data.db.Certificate, Exception, Object> deferred, String address, int port, int portCert) throws URISyntaxException, InterruptedException {
        MelCertRetriever retriever = new MelCertRetriever(melAuthService);
        retriever.retrieveCertificate(deferred, address, port, portCert);
    }

    private Promise<Certificate, Exception, Void> register(DeferredObject<Certificate, Exception, Void> result, Certificate certificate, String address, Integer port) throws IllegalAccessException, SqlQueriesException, URISyntaxException, InvalidKeyException, NoSuchAlgorithmException, JsonSerializationException, CertificateException, KeyStoreException, ClassNotFoundException, KeyManagementException, BadPaddingException, UnrecoverableKeyException, NoSuchPaddingException, IOException, IllegalBlockSizeException, InterruptedException {
        melAuthSocket = new MelAuthSocket(melAuthService);
//        Socket socket = melAuthService.getCertificateManager().createSocket();
//        socket.connect(new InetSocketAddress(address,port));
        MelRegisterProcess melRegisterProcess = new MelRegisterProcess(melAuthSocket);
        return melRegisterProcess.register(result, certificate.getId().v(), address, port);
    }

    @Override
    protected void workWork(Job job) throws Exception {
        if (job instanceof ConnectJob) {
//            Lok.debug("Connecting to: " + connectJob.getAddress() + ":" + connectJob.getPort() + "/" + connectJob.getPortCert());
//        MelAuthSocket melAuthSocket = new MelAuthSocket(melAuthService);
//        Socket socket = createSocket();
//        N.oneLine(() -> melAuthSocket.connect(connectJob));
            connect(connectJob);
        } else if (job instanceof IsolatedConnectJob) {
            connect((IsolatedConnectJob) job);
        }
        if (this.connectJob.isResolved())
            shutDown();

    }

    @Override
    public DeferredObject<Void, Void, Void> shutDown() {
        if (melAuthSocket != null)
            melAuthSocket.stop();
        super.shutDown();
        return null;
    }
}
