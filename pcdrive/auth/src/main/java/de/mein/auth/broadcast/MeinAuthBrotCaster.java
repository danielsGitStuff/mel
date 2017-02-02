package de.mein.auth.broadcast;

import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.Service;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;
import de.mein.sql.SqlQueriesException;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by xor on 9/23/16.
 */
public class MeinAuthBrotCaster extends BrotCaster {
    private final MeinAuthService meinAuthService;
    private MeinAuthBrotCasterListener brotCasterListener;

    public MeinAuthBrotCaster setBrotCasterListener(MeinAuthBrotCasterListener brotCasterListener) {
        this.brotCasterListener = brotCasterListener;
        return this;
    }

    public interface MeinAuthBrotCasterListener {
        void onHasAddress(InetAddress address, int port, int portCert);
    }

    public MeinAuthBrotCaster(MeinAuthService meinAuthService) {
        super(meinAuthService.getSettings().getBrotcastListenerPort(), meinAuthService.getSettings().getBrotcastPort());
        this.meinAuthService = meinAuthService;
    }

    private boolean handleBrotcast(DatagramPacket packet) throws SqlQueriesException, IOException {
        String brotCast = new String(packet.getData());
        // port stuff is not exactly valid
        final String greetingExtractionRegex = "^meinauth\\([0-9]{3}[0-9]{1,2},[0-9]{3}[0-9]{1,2}\\)\\.";
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
                String answerString = meinAuthService.getSettings().getDiscoverAnswer();
                DatagramPacket answer = new DatagramPacket(answerString.getBytes(), answerString.getBytes().length, packet.getAddress(), packet.getPort());
                socket.send(answer);
            }
            return true;
        }
        return false;
    }

    private void onCertificateSpotted(Certificate partnerCertificate) throws SqlQueriesException {
        // propagate good news to every allowed Service
        for (Service service : meinAuthService.getDatabaseManager().getAllowedServices(partnerCertificate.getId().v())) {
            IMeinService runningService = meinAuthService.getMeinService(service.getUuid().v());
            if (runningService != null)
                runningService.handleCertificateSpotted(partnerCertificate);
        }
    }

    private Certificate findCertificate(String greeting) throws SqlQueriesException {
        List<Certificate> possibleCertificates = meinAuthService.getCertificateManager().getCertificatesByGreeting(greeting);
        if (possibleCertificates.size() == 0)
            System.out.println("MeinAuthBrotCaster.findCertificate.don't know that guy");
        else if (possibleCertificates.size() > 1)
            System.out.println("MeinAuthBrotCaster.findCertificate.know too many of these guys");
        else {
            return possibleCertificates.get(0);
        }
        return null;
    }

    private void updateCertificate(Certificate partnerCertificate, DatagramPacket packet) throws SqlQueriesException {
        String brotCast = new String(packet.getData());
        Integer port = extractPort(brotCast);
        Integer deliveryPort = extractDeliveryPort(brotCast);
        partnerCertificate.setPort(port).setCertDeliveryPort(deliveryPort).setAddress(packet.getAddress().getHostAddress());
        meinAuthService.getCertificateManager().updateCertificate(partnerCertificate);
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
        final String extractionRegex = "^meinauth\\.answer\\([0-9]{3}[0-9]{1,2},[0-9]{3}[0-9]{1,2}\\)\\.";
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
        String brotCast = new String(buf);
        System.out.println(meinAuthService.getName() + ".MeinAuthBrotCaster.handleMessage.msg: " + brotCast);
        try {
            if (!handleDiscover(packet))
                if (!handleDiscoverAnswer(packet))
                    if (!handleBrotcast(packet))
                        handleBrotcastAnswer(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("Duplicates")
    private boolean handleDiscover(DatagramPacket packet) throws IOException {
        String brotCast = new String(packet.getData()).trim();
        // port stuff is not exactly valid
        final String disciverRegex = "^meinauth\\.discover\\([0-9]{3}[0-9]{1,2},[0-9]{3}[0-9]{1,2}\\)";
        Pattern pattern = Pattern.compile(disciverRegex);
        Matcher matcher = pattern.matcher(brotCast);
        if (matcher.matches()) {
            if (brotCasterListener != null)
                brotCasterListener.onHasAddress(packet.getAddress(), extractPort(brotCast), extractDeliveryPort(brotCast));
            String answerString = meinAuthService.getSettings().getDiscoverAnswer();
            DatagramPacket answer = new DatagramPacket(answerString.getBytes(), answerString.getBytes().length, packet.getAddress(), packet.getPort());
            socket.send(answer);
            return true;
        }
        return false;
    }

    private boolean handleDiscoverAnswer(DatagramPacket packet) throws SqlQueriesException {
        String msg = new String(packet.getData()).trim();
        final String answerRegex = "^meinauth\\.discover\\.answer\\([0-9]{3}[0-9]{1,2},[0-9]{3}[0-9]{1,2}\\)";
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
        this.brotcast(port, meinAuthService.getSettings().getDiscoverMessage());
    }

}
