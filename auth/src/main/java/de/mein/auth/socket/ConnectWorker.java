package de.mein.auth.socket;

import de.mein.Lok;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.jobs.AConnectJob;
import de.mein.auth.jobs.ConnectJob;
import de.mein.auth.jobs.IsolatedConnectJob;
import de.mein.auth.jobs.Job;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.MeinWorker;
import de.mein.auth.socket.process.imprt.MeinCertRetriever;
import de.mein.auth.tools.CountdownLock;
import de.mein.auth.tools.N;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.sql.SqlQueriesException;
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
public class ConnectWorker extends MeinWorker {

    private final AConnectJob connectJob;
    private final MeinAuthService meinAuthService;
    private MeinAuthSocket meinAuthSocket;

    public ConnectWorker(MeinAuthService meinAuthService, AConnectJob connectJob) {
        Objects.requireNonNull(connectJob);
        this.meinAuthService = meinAuthService;
        this.connectJob = connectJob;
        connectJob.getPromise().always((state, resolved, rejected) -> shutDown());
        addJob(connectJob);
    }


    @Override
    public String getRunnableName() {
        String line = "Connecting to: " + connectJob.getAddress() + ":" + connectJob.getPort() + "/" + connectJob.getPortCert();
        return line;
    }


    private DeferredObject<MeinValidationProcess, Exception, Void> auth(AConnectJob originalJob) {
        ConnectJob dummyJob = new ConnectJob(originalJob.getCertificateId(), originalJob.getAddress(), originalJob.getPort(), originalJob.getPortCert(), false);
//        DeferredObject<MeinValidationProcess, Exception, Void> deferred = dummyJob.getPromise();
        N runner = new N(e -> {
            e.printStackTrace();
            dummyJob.getPromise().reject(e);
        });
        runner.runTry(() -> {
            meinAuthSocket = new MeinAuthSocket(meinAuthService, dummyJob);
            meinAuthSocket.setRunnableName("-> " + originalJob.getAddress() + ":" + connectJob.getPort());
//            Socket socket = meinAuthService.getCertificateManager().createSocket();
//            socket.connect(new InetSocketAddress(job.getAddress(), job.getPort()));
            MeinAuthProcess meinAuthProcess = new MeinAuthProcess(meinAuthSocket);
            meinAuthProcess.authenticate(dummyJob);
        });
        return dummyJob.getPromise();
    }

    /**
     * connects and blocks
     *
     * @param job
     * @return
     */
    Promise<MeinValidationProcess, Exception, Void> connect(AConnectJob job) {
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

        Lok.debug("MeinAuthSocket.connect(id=" + remoteCertId + " addr=" + address + " port=" + port + " portCert=" + portCert + " reg=" + regOnUnknown + ")");
        meinAuthService.getPowerManager().wakeLock(this);
        DeferredObject result = job.getPromise();
        N runner = new N(e -> {
            result.reject(e);
            meinAuthService.getPowerManager().releaseWakeLock(this);
            stopConnecting();
            lock.unlock();
        });
        DeferredObject<MeinValidationProcess, Exception, Void> firstAuth = this.auth(job);
        firstAuth.done(result1 -> {
            result.resolve(result1);
            meinAuthService.getPowerManager().releaseWakeLock(this);
        }).fail(except -> runner.runTry(() -> {
            if (except instanceof ShamefulSelfConnectException) {
                result.reject(except);
                meinAuthService.getPowerManager().releaseWakeLock(this);
                shutDown();
            } else if (except instanceof ConnectException) {
                Lok.error(getClass().getSimpleName() + " for " + meinAuthService.getName() + ".connect.HOST:NOT:REACHABLE");
                result.reject(except);
                meinAuthService.getPowerManager().releaseWakeLock(this);
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
                                secondJob.getPromise().done(result1 -> {
                                    result.resolve(result1);
                                    shutDown();
                                }).fail(result1 -> {
                                    result.resolve(result1);
                                    shutDown();
                                });
                                this.addJob(secondJob);
                            });

                        }).fail(exception -> {
                                    // it won't compile otherwise. don't know why.
                                    // compiler thinks exception is an Object instead of Exception
                                    ((Exception) exception).printStackTrace();
                                    result.reject(exception);
                                    meinAuthService.getPowerManager().releaseWakeLock(this);
                                    stopConnecting();
                                }
                        );
                    });
                }).fail(ee -> {
                    ee.printStackTrace();
                    result.reject(ee);
                    meinAuthService.getPowerManager().releaseWakeLock(this);
                    stopConnecting();
                });
            } else {
                if (!(except instanceof ShamefulSelfConnectException)) {
                    result.reject(new CannotConnectException(except, address, port));
                } else {
                    result.reject(except);
                }
                meinAuthService.getPowerManager().releaseWakeLock(this);
                stopConnecting();
            }
        }));

        /*else {
            Lok.debug("MeinAuthSocket.connect.NOT.IMPLEMENTED.YET");
            this.auth(result, remoteCertId, address, port, portCert);
        }*/
        return result;
    }

    private void stopConnecting() {
        if (meinAuthSocket != null)
            meinAuthSocket.stop();
        shutDown();
    }

    public void importCertificate(DeferredObject<de.mein.auth.data.db.Certificate, Exception, Object> deferred, String address, int port, int portCert) throws URISyntaxException, InterruptedException {
        MeinCertRetriever retriever = new MeinCertRetriever(meinAuthService);
        retriever.retrieveCertificate(deferred, address, port, portCert);
    }

    private Promise<Certificate, Exception, Void> register(DeferredObject<Certificate, Exception, Void> result, Certificate certificate, String address, Integer port) throws IllegalAccessException, SqlQueriesException, URISyntaxException, InvalidKeyException, NoSuchAlgorithmException, JsonSerializationException, CertificateException, KeyStoreException, ClassNotFoundException, KeyManagementException, BadPaddingException, UnrecoverableKeyException, NoSuchPaddingException, IOException, IllegalBlockSizeException, InterruptedException {
        meinAuthSocket = new MeinAuthSocket(meinAuthService);
//        Socket socket = meinAuthService.getCertificateManager().createSocket();
//        socket.connect(new InetSocketAddress(address,port));
        MeinRegisterProcess meinRegisterProcess = new MeinRegisterProcess(meinAuthSocket);
        return meinRegisterProcess.register(result, certificate.getId().v(), address, port);
    }

    @Override
    protected void workWork(Job job) throws Exception {
        if (job instanceof ConnectJob) {
            Lok.debug("Connecting to: " + connectJob.getAddress() + ":" + connectJob.getPort() + "/" + connectJob.getPortCert());
//        MeinAuthSocket meinAuthSocket = new MeinAuthSocket(meinAuthService);
//        Socket socket = createSocket();
//        N.oneLine(() -> meinAuthSocket.connect(connectJob));
            connect(connectJob);
        } else if (job instanceof IsolatedConnectJob) {
            connect((IsolatedConnectJob) job);
        }
        if (this.connectJob.getPromise().isResolved())
            shutDown();

    }

    @Override
    public void shutDown() {
        super.shutDown();
    }
}
