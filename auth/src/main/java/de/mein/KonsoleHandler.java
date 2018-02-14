package de.mein;

import de.mein.auth.data.MeinAuthSettings;
import de.mein.sql.Pair;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by xor on 1/15/17.
 */
public class KonsoleHandler {

	private Pair<String> stringPair(String key, String value) {
		return new Pair<String>(String.class, key, value);
	}

	protected void fillParameters(Pair... pairs) {
		parameters = new ArrayList<>();
		if (pairs != null) {
			for (Pair pair : pairs) {
				parameters.add(pair);
			}
		}
	}

	public KonsoleHandler() {
		fillParameters(workingDirectory, port, deliveryPort, broadcastPort, broadcastListenerPort);
	}

	private final String jsonPath = "meinAuth.settings.json";
	private int position = 0;
	private List<Pair<String>> parameters;
	private Pair<String> broadcastPort = stringPair("-bcp", "port used for broadcasting");
	private Pair<String> port = stringPair("-p", "port used for messaging (listening and sending)");
	private Pair<String> deliveryPort = stringPair("-dp", "port used to deliver the certificate");
	private Pair<String> workingDirectory = stringPair("-d",
			"path of working directory\nmeinauth will store certificates there");
	private Pair<String> broadcastListenerPort = stringPair("-bclp", "listen for broadcasts on this port");
	MeinAuthSettings meinAuthSettings = MeinAuthSettings.createDefaultSettings();
	private String[] args;

	public MeinAuthSettings start(String[] args) throws Exception {
		this.args = args;
		if (args.length > 0 && args[0].equals("--help")) {
			displayHelp();
			System.exit(0);
		} else if (args.length > 0) {
			while (position < args.length)
				read();
			meinAuthSettings.setJsonFile(new File(jsonPath));
		} else {
			File jsonFile = new File(jsonPath);
			if (jsonFile.exists()) {
				System.out.println("KonsoleHandler.start.loading settings from file: " + jsonFile.getAbsolutePath());
				meinAuthSettings = (MeinAuthSettings) MeinAuthSettings.load(jsonFile);
			} else
				meinAuthSettings = MeinAuthSettings.createDefaultSettings();
		}
		return meinAuthSettings;
	}

	private void read() {
		String key = args[position++];
		String value = args[position++];
		assert key != null && value != null;
		if (key.equals(workingDirectory.k()))
			meinAuthSettings.setWorkingDirectory(new File(value));
		else if (key.equals(port.k()))
			meinAuthSettings.setPort(Integer.parseInt(value));
		else if (key.equals(deliveryPort.k()))
			meinAuthSettings.setDeliveryPort(Integer.parseInt(value));
		else if (key.equals(broadcastPort.k()))
			meinAuthSettings.setBrotcastPort(Integer.parseInt(value));
		else if (key.equals(broadcastListenerPort.k()))
			meinAuthSettings.setBrotcastListenerPort(Integer.parseInt(value));
	}

	private void displayHelp() {
		System.out.println("MeinAuth Help: available arguments:");
		for (Pair<String> parameter : parameters) {
			System.out.println(parameter.k() + ": " + parameter.v());
		}
	}
}
