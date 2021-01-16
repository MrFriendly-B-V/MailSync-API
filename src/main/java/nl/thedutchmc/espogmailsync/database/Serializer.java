package nl.thedutchmc.espogmailsync.database;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.apache.commons.lang3.exception.ExceptionUtils;

import nl.thedutchmc.espogmailsync.App;

public class Serializer {

	/**
	 * Method used to serialize an Object into a Byte[]
	 * @param object Object to serialize, must implement Serializable
	 * @return Returns the serialized object as a byte[]
	 */
	public static byte[] serializeObject(Serializable object) {
		ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		ObjectOutputStream out = null; 
		
		byte[] output = null;
		try {
			out = new ObjectOutputStream(byteOut);
			out.writeObject(object);
			out.flush();

			output = byteOut.toByteArray();
		} catch (IOException e) {
			App.logError("Unable to serialize Object due to an IOException");
			App.logDebug(ExceptionUtils.getStackTrace(e));
		} finally {
			try {
				out.close();
				byteOut.close();
			} catch(IOException e) {
				App.logError("Unable to serialize Object due to an IOException");
				App.logDebug(ExceptionUtils.getStackTrace(e));
			}
		}
		
		return output;
	}
	
	/**
	 * Deserialize a serialized object
	 * @param input Byte[] of the serialized object
	 * @return Returns deserialized object 
	 */
	public static Object deserializeObject(byte[] input) {
		ByteArrayInputStream byteIn = new ByteArrayInputStream(input);
		ObjectInputStream in = null;
		
		Object o = null;
		try {
			in = new ObjectInputStream(byteIn);
			o = in.readObject();
		} catch(IOException e) {
			App.logError("Unable to deserialize byte[] due to an IOException");
			App.logDebug(ExceptionUtils.getStackTrace(e));
		} catch (ClassNotFoundException e) {
			App.logError("Unable to deserialize byte[] due to a ClassNotFoundException");
			App.logDebug(ExceptionUtils.getStackTrace(e));
		} finally {
			try {
				in.close();
				byteIn.close();
			} catch(IOException e) {
				App.logError("Unable to deserialize byte[] due to an IOException");
				App.logDebug(ExceptionUtils.getStackTrace(e));
			}
		}
		
		return o;
	}
}
