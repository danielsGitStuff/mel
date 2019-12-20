package de.mel.auth.broadcast;

import de.mel.DeferredRunnable;
import de.mel.Lok;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.data.db.Service;
import de.mel.auth.service.IMelService;
import de.mel.auth.service.MelAuthServiceImpl;
import de.mel.sql.SqlQueriesException;
import org.jdeferred.Promise;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends and retrieves small messages from the broadcast network address. <br>
 * Messages are announcements of new MelAuth instances who say "Hello"
 */
public class MelAuthBrotCaster extends BrotCaster {
    private final MelAuthServiceImpl melAuthService;
    private MelAuthBrotCasterListener brotCasterListener;

    public MelAuthBrotCaster setBrotCasterListener(MelAuthBrotCasterListener brotCasterListener) {
        this.brotCasterListener = brotCasterListener;
        return this;
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + melAuthService.getName();
    }


    public interface MelAuthBrotCasterListener {
        void onHasAddress(InetAddress address, int port, int portCert);
    }

    public MelAuthBrotCaster(MelAuthServiceImpl melAuthService) {
        super(melAuthService.getSettings().getBrotcastListenerPort(), melAuthService.getSettings().getBrotcastPort());
        this.melAuthService = melAuthService;
    }

    private boolean handleBrotcast(DatagramPacket packet) throws SqlQueriesException, IOException {
        String brotCast = new String(packet.getData());
        // port stuff is not exactly valid
        final String greetingExtractionRegex = "^melauth\\([0-9]{3}[0-9]{1,2},[0-9]{3}[0-9]{1,2}\\)\\.";
        final String greetingMatchRegex = greetingExtractionRegex + ".+";
        Pattern pattern = Pattern.compile(greetingMatchRegex);
        Matcher matcher = pattern.matcher(brotCast);
        if (matcher.matches()) {
            //find the dude who sent it and greet back!
            String greeting = brotCast.replaceAll(greetingExtractionRegex, "").trim();
            Certificate partnerCertificate = findCertificate(greeting);
            if (partnerCertificate != null) {
                this.updateCertificate(partnerCertificate, packet);
                this.onCertificateSpotted(partnerCertificate);
                if (this.brotCasterListener != null)
                    this.brotCasterListener.onHasAddress(packet.getAddress(), packet.getPort(), extractDeliveryPort(brotCast));
                String answerString = melAuthService.getSettings().getDiscoverAnswer();
                DatagramPacket answer = new DatagramPacket(answerString.getBytes(), answerString.getBytes().length, packet.getAddress(), packet.getPort());
                socket.send(answer);
            }
            return true;
        }
        return false;
    }

    private void onCertificateSpotted(Certificate partnerCertificate) throws SqlQueriesException {
        // propagate good news to every allowed Service
        for (Service service : melAuthService.getDatabaseManager().getAllowedServices(partnerCertificate.getId().v())) {
            IMelService runningService = melAuthService.getMelService(service.getUuid().v());
            if (runningService != null)
                runningService.handleCertificateSpotted(partnerCertificate);
        }
    }

    private Certificate findCertificate(String greeting) throws SqlQueriesException {
//        List<Certificate> possibleCertificates = melAuthService.getCertificateManager().getCertificatesByGreeting(greeting);
//        if (possibleCertificates.size() == 0)
//            Lok.debug("MelAuthBrotCaster.findCertificate.don't know that guy");
//        else if (possibleCertificates.size() > 1)
//            Lok.debug("MelAuthBrotCaster.findCertificate.know too many of these guys");
//        else {
//            return possibleCertificates.get(0);
//        }
        return null;
    }

    private void updateCertificate(Certificate partnerCertificate, DatagramPacket packet) throws SqlQueriesException {
        String brotCast = new String(packet.getData());
        Integer port = extractPort(brotCast);
        Integer deliveryPort = extractDeliveryPort(brotCast);
        partnerCertificate.setPort(port).setCertDeliveryPort(deliveryPort).setAddress(packet.getAddress().getHostAddress());
        melAuthService.getCertificateManager().updateCertificate(partnerCertificate);
    }

    private Integer extractPort(String msg) {
        int bracket = msg.indexOf('(') + 1;
        int comma = msg.indexOf(',');
        String portString = msg.substring(bracket, comma);
        return Integer.parseInt(portString);
    }

    private Integer extractDeliveryPort(String msg) {
        int bracket = msg.indexOf(',') + 1;
        int comma = msg.indexOf(')');
        String portString = msg.substring(bracket, comma);
        return Integer.parseInt(portString);
    }

    private boolean handleBrotcastAnswer(DatagramPacket packet) throws SqlQueriesException {
        String msg = new String(packet.getData());
        final String extractionRegex = "^melauth\\.answer\\([0-9]{3}[0-9]{1,2},[0-9]{3}[0-9]{1,2}\\)\\.";
        final String matchRegex = extractionRegex + ".+";
        Pattern pattern = Pattern.compile(matchRegex);
        Matcher matcher = pattern.matcher(msg);
        if (matcher.matches()) {
            String greeting = msg.replaceFirst(extractionRegex, "").trim();
            Certificate partnerCertificate = findCertificate(greeting);
            if (partnerCertificate != null) {
                this.updateCertificate(partnerCertificate, packet);
                this.onCertificateSpotted(partnerCertificate);
            }
            return true;
        }
        return false;
    }

    @Override
    protected void handleMessage(DatagramPacket packet, byte[] buf) {
        String brotCast = new String(buf).trim();
        Lok.debug(melAuthService.getName() + ".MelAuthBrotCaster.handleMessage.msg: '" + brotCast + "' from address " + packet.getAddress().getHostName());
        try {
            if (!handleDiscover(packet))
                if (!handleDiscoverAnswer(packet))
                    if (!handleBrotcast(packet))
                        handleBrotcastAnswer(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Promise<Void, Void, Void> onShutDown() {
        super.onShutDown();
        brotCasterListener = null;
        return DeferredRunnable.ResolvedDeferredObject();
    }

    @SuppressWarnings("Duplicates")
    private boolean handleDiscover(DatagramPacket packet) throws IOException {
        String brotCast = new String(packet.getData()).trim();
        // port stuff is not exactly valid
        final String disciverRegex = "^melauth\\.discover\\([0-9]{3}[0-9]{1,2},[0-9]{3}[0-9]{1,2}\\)";
        Pattern pattern = Pattern.compile(disciverRegex);
        Matcher matcher = pattern.matcher(brotCast);
        if (matcher.matches()) {
//            if (brotCasterListener != null)
//                brotCasterListener.onHasAddress(packet.getAddress(), extractPort(brotCast), extractDeliveryPort(brotCast));
            String answerString = melAuthService.getSettings().getDiscoverAnswer();
            DatagramPacket answer = new DatagramPacket(answerString.getBytes(), answerString.getBytes().length, packet.getAddress(), packet.getPort());
            socket.send(answer);
            return true;
        }
        return false;
    }

    private boolean handleDiscoverAnswer(DatagramPacket packet) throws SqlQueriesException {
        String msg = new String(packet.getData()).trim();
        final String answerRegex = "^melauth\\.discover\\.answer\\([0-9]{3}[0-9]{1,2},[0-9]{3}[0-9]{1,2}\\)";
        Pattern pattern = Pattern.compile(answerRegex);
        Matcher matcher = pattern.matcher(msg);
        if (matcher.matches()) {
            if (brotCasterListener != null) {
                brotCasterListener.onHasAddress(packet.getAddress(), extractPort(msg), extractDeliveryPort(msg));
            }
            return true;
        }
        return false;
    }

    public void discover(int port) throws IOException {
        this.brotcast(port, melAuthService.getSettings().getDiscoverMessage());
    }

}
