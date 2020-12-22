package nl.thedutchmc.espogmailsync;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import nl.thedutchmc.espogmailsync.runnables.mailobjects.Message;
import nl.thedutchmc.espogmailsync.runnables.mailobjects.MessageThread;

@SpringBootApplication
public class App {

	public static final boolean DEBUG = true;
	
	private static HashMap<String, Message> messages = new HashMap<>();
	private static HashMap<String, MessageThread> threads = new HashMap<>();
	
	public static List<String> messagesAnalysed = new ArrayList<>();
	public static List<String> threadsAnalysed = new ArrayList<>();
	
    public static void main(String[] args) {
    	new Config().readConfig();
    	
		//Start the Spring boot server
		SpringApplication.run(App.class, args);
    }
    
    public static void addAllMessages(HashMap<String, Message> messages) {
    	App.messages.putAll(messages);
    }
    
    public static void addAllThreads(HashMap<String, MessageThread> threads) {
    	App.threads.putAll(threads);
    }
    
    public static HashMap<String, Message> getMessages() {
    	return App.messages;
    }
    
    public static MessageThread getMessageThread(String threadId) {
    	return App.threads.get(threadId);
    }
	public static void logDebug(Object log) {
		if(!DEBUG) return;
		
		//kk:mm:ss --> hour:minute:seconds, without hours going 0-24
		final DateTimeFormatter f = DateTimeFormatter.ofPattern("kk:mm:ss");
		LocalDateTime now = LocalDateTime.now();
		System.out.println("[" + now.format(f) + "][DEBUG] " + log.toString());
	}
	
	public static void logInfo(Object log) {
		//kk:mm:ss --> hour:minute:seconds, without hours going 0-24
		final DateTimeFormatter f = DateTimeFormatter.ofPattern("kk:mm:ss");
		LocalDateTime now = LocalDateTime.now();
		System.out.println("[" + now.format(f) + "][INFO] " + log.toString());
	}
	
	public static void logError(Object log) {
		//kk:mm:ss --> hour:minute:seconds, without hours going 0-24
		final DateTimeFormatter f = DateTimeFormatter.ofPattern("kk:mm:ss");
		LocalDateTime now = LocalDateTime.now();
		System.err.println("[" + now.format(f) + "][ERROR] " + log.toString());
	}   
}
