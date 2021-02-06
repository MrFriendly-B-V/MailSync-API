package nl.thedutchmc.espogmailsync;

import java.io.IOException;
import java.net.MalformedURLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONObject;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;

import nl.thedutchmc.espogmailsync.database.DatabaseGetMail;
import nl.thedutchmc.espogmailsync.database.SqlManager;
import nl.thedutchmc.espogmailsync.database.SyncMailUsers;
import nl.thedutchmc.espogmailsync.runnables.EspoSyncRunnable;
import nl.thedutchmc.espogmailsync.runnables.FetchMailRunnable;
import nl.thedutchmc.espogmailsync.mailobjects.Message;
import nl.thedutchmc.espogmailsync.mailobjects.MessageThread;
import nl.thedutchmc.httplib.Http;
import nl.thedutchmc.httplib.Http.RequestMethod;
import nl.thedutchmc.httplib.Http.ResponseObject;

@SpringBootApplication
@PropertySource("classpath:application.properties")
public class App {

	public static final boolean DEBUG = false;
	
	public static List<User> activeUsers = new ArrayList<>();
	public static List<String> messagesAnalysed = new ArrayList<>();
	public static List<String> threadsAnalysed = new ArrayList<>();
	
	private static SqlManager sqlManager;
		
    public static void main(String[] args) {
    	
    	new Config().readConfig();
    	
    	sqlManager = new SqlManager();
    	
    	DatabaseGetMail dbGetMail = new DatabaseGetMail();
    	HashMap<String, Message> messages = dbGetMail.getMessages();    	
    	messages.forEach((id, m) -> {
    		messagesAnalysed.add(id);
    	});
    	messages = null;
    	
    	HashMap<String, MessageThread> messageThreads = dbGetMail.getMessageThreads();
    	messageThreads.forEach((id, mt) -> {
    		threadsAnalysed.add(id);
    	});
    	messageThreads = null;
    	
    	final String endpoint = Config.authServerHost + "/oauth/token";

    	SyncMailUsers smu = new SyncMailUsers();
    	String[] ids = smu.getMailUsers();
    	for(String id : ids) {
        	final HashMap<String, String> params = new HashMap<>();
        	params.put("apiToken", Config.apiToken);
        	params.put("userId", id);
        	
        	ResponseObject responseObject = null;
        	try {
        		responseObject = new Http(App.DEBUG).makeRequest(
        				RequestMethod.POST,
        				endpoint, 
        				params, 
        				null, //Body MIME type
        				null, //Body
        				null);//Headers
        	} catch (MalformedURLException e) {
        		App.logError("Unable to communicate with AuthServer. Check your config. Caused by MalformedURLException. Exiting");
        		App.logDebug(ExceptionUtils.getStackTrace(e));
        		System.exit(1);
        	} catch (IOException e) {
        		App.logError("Unable to communicate with AuthServer. Caused by IOException. Exiting");
        		App.logDebug(ExceptionUtils.getStackTrace(e));
        		System.exit(1);
        	}
        	
    		if(responseObject.getResponseCode() != 200) {
    			switch(responseObject.getResponseCode()) {
    			case 403: 
    				App.logError("Invalid API token. Check your config. Exiting");
    				System.exit(1);
    				break;
    			case 404:
    				App.logDebug("No user with ID " + id + " known to the AuthServer. Removing from sync list!");
    				//TODO remove ID from sync list.
    				break;
    			default:
    				App.logError("An unexpected status was returned from the AuthServer. Check your config and the health of the AuthServer");
    				App.logDebug(responseObject.getConnectionMessage());
    				break;
    			}
    		}
    		
    		JSONObject jsonResponse = new JSONObject(responseObject.getMessage());
    		User user = new User(jsonResponse.getString("id"), jsonResponse.getString("token"), jsonResponse.getString("email"));
    		
    		App.activeUsers.add(user);
    	}
    	
    	//Start fetch mail thread for each user
    	App.activeUsers.forEach(user -> {
    		String id = user.getId();
    		String token = user.getToken();
    		
        	Thread fetchMailThread = new Thread(new FetchMailRunnable(token, id), "FetchMailThread-" + id);
        	fetchMailThread.start();
    	});
    	
    	//Create a scheduled executor
    	final ScheduledThreadPoolExecutor scheduledExecutor = new ScheduledThreadPoolExecutor(3);
    	
    	//Schedule the Espo sync thread to start in 5 minutes
    	Thread espoSyncThread = new Thread(new EspoSyncRunnable(), "EspoSyncThread");
    	scheduledExecutor.schedule(() -> espoSyncThread.start(), 5, TimeUnit.MINUTES);
    	//espoSyncThread.start();
    	
		//Start the Spring boot server
		SpringApplication.run(App.class, args);
    }
    
    public static SqlManager getSqlManager() {
    	return App.sqlManager;
    }
    
	public static void logDebug(Object log) {
		if(!DEBUG) return;
		
		//kk:mm:ss --> hour:minute:seconds, hours using 24hr clock
		final DateTimeFormatter f = DateTimeFormatter.ofPattern("kk:mm:ss");
		LocalDateTime now = LocalDateTime.now();
		System.out.println("[" + now.format(f) + "][DEBUG] " + log);
	}
	
	public static void logInfo(Object log) {
		//kk:mm:ss --> hour:minute:seconds, hours using 24hr clock
		final DateTimeFormatter f = DateTimeFormatter.ofPattern("kk:mm:ss");
		LocalDateTime now = LocalDateTime.now();
		System.out.println("[" + now.format(f) + "][INFO] " + log);
	}
	
	public static void logError(Object log) {
		//kk:mm:ss --> hour:minute:seconds, hours using 24hr clock
		final DateTimeFormatter f = DateTimeFormatter.ofPattern("kk:mm:ss");
		LocalDateTime now = LocalDateTime.now();
		System.err.println("[" + now.format(f) + "][ERROR] " + log);
	}   
}
