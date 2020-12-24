package nl.thedutchmc.espogmailsync;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONObject;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class Config {
	
	public static String authServerHost;
	public static String apiToken;
	public static String frontendHost;
	public static String espoHost, espoApiKey, espoSecretKey;
	
	private static String configDirPath;
	public Config() {
		//Determine the configuration directory
		// For linux: /etc/espogmailsync
		// For Windows: C:\Program Files\Espo Gmail Sync
		// For other OS's: Directory where the JAR file of this program is located
		if(SystemUtils.IS_OS_LINUX) {
			configDirPath = "/etc/espogmailsync";
		} else if(SystemUtils.IS_OS_WINDOWS) {
			configDirPath = "C:\\Program Files\\Espo Gmail Sync";
		} else {
			try {
				final File jarPath = new File(Config.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
				final File folderPath = new File(jarPath.getParentFile().getPath());
				configDirPath = folderPath.getAbsolutePath();
			} catch(URISyntaxException e) {
				App.logError("Unable to determine config path due to an URISyntaxException. Exiting!");
				App.logDebug(e.getStackTrace());
				
				System.exit(1);
			}
		}		
	}

	public void readConfig() {
		App.logInfo("Reading config file...");
		File configFile = new File(configDirPath, "config.json");

		//Check if the config file exists,
		//if not create it and write the default config
		if(!configFile.exists()) {
			try {
				File configDir = new File(configDirPath);
				configDir.mkdirs();
				
				configFile.createNewFile();
				
				BufferedWriter bw = new BufferedWriter(new FileWriter(configFile));
				
				JSONObject defaultConfig = new JSONObject();
				defaultConfig.put("authServerHost", "");
				defaultConfig.put("apiToken", "");
				defaultConfig.put("frontendHost", "");
				defaultConfig.put("espoHost", "");
				defaultConfig.put("espoApiKey", "");
				defaultConfig.put("espoSecretKey", "");
						
				bw.write(defaultConfig.toString());
				bw.flush();
				bw.close();
			} catch (IOException e) {
				App.logError("Unable to create config.json due to an IOException. Exiting");
				App.logDebug(ExceptionUtils.getStackTrace(e));
				
				System.exit(1);
			}
		}
		
		//We can now read the config
		List<String> fileContentList = null;
		try {
			fileContentList = Files.readLines(configFile, Charsets.UTF_8);
		} catch (IOException e) {
			App.logError("Unable to read config.json due to an IOException. Exiting");
			App.logDebug(ExceptionUtils.getStackTrace(e));
			
			System.exit(1);
		}
		
		//Parse the config file
		JSONObject configJson = new JSONObject(String.join("", fileContentList));
		
		authServerHost = configJson.getString("authServerHost");
		apiToken = configJson.getString("apiToken");
		frontendHost = configJson.getString("frontendHost");
		
		espoHost = configJson.getString("espoHost");
		espoApiKey = configJson.getString("espoApiKey");
		espoSecretKey = configJson.getString("espoSecretKey");
		
		App.logInfo("Completed reading configuration file.");
	}
}
