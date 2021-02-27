package nl.thedutchmc.espogmailsync;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

public class EspoUtils {
	
	public enum HttpMethod {
		GET,
		POST,
		PUT
	}
	
	/**
	 * Method used to get the HMAC authorization String
	 * @param method HTTP method used, e.g GET or POST
	 * @param path Path the request is going to, that is anything after /api/v1/
	 * @return Returns the HMAC authorization String
	 */
	public static String getHmacAuthorization(HttpMethod method, String path) {
		//Setup the hashing algorithm
		Mac sha256_HMAC = null;
		try {
			sha256_HMAC = Mac.getInstance("HmacSHA256");
			SecretKeySpec secretKey = new SecretKeySpec(App.getEnvironment().getEspoSecretKey().getBytes(), "HmacSHA256");
			sha256_HMAC.init(secretKey);
		} catch (NoSuchAlgorithmException e) {
			// We don't need to handle this exception, since the `HmacSHA256` algorithm is always there
		} catch (InvalidKeyException e) {
			App.logError("Invalid espoSecretKey. Please double check your config!");
			App.logDebug(ExceptionUtils.getStackTrace(e));
		}
		
		//Get the hash
		//Compose of (method + ' /' + path)
		//Where method: GET, POST etc
		//Where path: Account, Contact etc
		byte[] hash = sha256_HMAC.doFinal((method.toString() + " /" + path).getBytes());
		
		//Compose the final list of Bytes
		//Compose of apiKey + ':' + hash
		//String#getBytes() returns a byte[], so we first have to turn it into
		//a Byte[], then put it in a List<Byte> before we can add it.
		List<Byte> hmacBytes = new ArrayList<>();
		hmacBytes.addAll(Arrays.asList(ArrayUtils.toObject((App.getEnvironment().getEspoApiKey() + ":").getBytes())));
		hmacBytes.addAll(Arrays.asList(ArrayUtils.toObject(hash)));
		
		//Get the final hmacAuthorization value
		//First turn the hmacBytes<Byte> into a byte[],
		//Then encode it as base64
		String hmacAuthorization = Base64.getEncoder().encodeToString(ArrayUtils.toPrimitive(hmacBytes.toArray(new Byte[0])));
		
		//Finally return that base64 String
		return hmacAuthorization;
	}
	
	/**
	 * Build URL string from Map of params. Nested Map and Collection is also supported
	 *
	 * @param params   Map of params for constructing the URL Query String
	 * @param encoding encoding type. If not set the "UTF-8" is selected by default
	 * @return String of type key=value&...key=value
	 * @throws java.io.UnsupportedEncodingException
	 *          if encoding isnot supported
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static String httpBuildQuery(Map<String, Object> params, String encoding) {
	    if (isEmpty(encoding)) {
	        encoding = "UTF-8";
	    }
	    StringBuilder sb = new StringBuilder();
	    for (Map.Entry<String, Object> entry : params.entrySet()) {
	        if (sb.length() > 0) {
	            sb.append('&');
	        }

	        String name = entry.getKey();
	        Object value = entry.getValue();


	        if (value instanceof Map) {
	            List<String> baseParam = new ArrayList<String>();
	            baseParam.add(name);
	            String str = buildUrlFromMap(baseParam, (Map) value, encoding);
	            sb.append(str);

	        } else if (value instanceof Collection) {
	            List<String> baseParam = new ArrayList<String>();
	            baseParam.add(name);
	            String str = buildUrlFromCollection(baseParam, (Collection) value, encoding);
	            sb.append(str);

	        } else {
	            sb.append(encodeParam(name));
	            sb.append("=");
	            sb.append(encodeParam(value));
	        }


	    }
	    return sb.toString();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static String buildUrlFromMap(List<String> baseParam, Map<Object, Object> map, String encoding) {
	    StringBuilder sb = new StringBuilder();
	    String token;

	    //Build string of first level - related with params of provided Map
	    for (Map.Entry<Object, Object> entry : map.entrySet()) {

	        if (sb.length() > 0) {
	            sb.append('&');
	        }

	        String name = String.valueOf(entry.getKey());
	        Object value = entry.getValue();
	        if (value instanceof Map) {
	            List<String> baseParam2 = new ArrayList<String>(baseParam);
	            baseParam2.add(name);
	            String str = buildUrlFromMap(baseParam2, (Map) value, encoding);
	            sb.append(str);

	        } else if (value instanceof List) {
	            List<String> baseParam2 = new ArrayList<String>(baseParam);
	            baseParam2.add(name);
	            String str = buildUrlFromCollection(baseParam2, (List) value, encoding);
	            sb.append(str);
	        } else {
	            token = getBaseParamString(baseParam) + "[" + name + "]=" + encodeParam(value);
	            sb.append(token);
	        }
	    }

	    return sb.toString();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static String buildUrlFromCollection(List<String> baseParam, Collection coll, String encoding) {
	    StringBuilder sb = new StringBuilder();
	    String token;
	    if (!(coll instanceof List)) {
	        coll = new ArrayList(coll);
	    }
	    List arrColl = (List) coll;

	    //Build string of first level - related with params of provided Map
	    for (int i = 0; i < arrColl.size(); i++) {

	        if (sb.length() > 0) {
	            sb.append('&');
	        }

	        Object value = (Object) arrColl.get(i);
	        if (value instanceof Map) {
	            List<String> baseParam2 = new ArrayList<String>(baseParam);
	            baseParam2.add(String.valueOf(i));
	            String str = buildUrlFromMap(baseParam2, (Map) value, encoding);
	            sb.append(str);

	        } else if (value instanceof List) {
	            List<String> baseParam2 = new ArrayList<String>(baseParam);
	            baseParam2.add(String.valueOf(i));
	            String str = buildUrlFromCollection(baseParam2, (List) value, encoding);
	            sb.append(str);
	        } else {
	            token = getBaseParamString(baseParam) + "[" + i + "]=" + encodeParam(value);
	            sb.append(token);
	        }
	    }

	    return sb.toString();
	}


	private static String getBaseParamString(List<String> baseParam) {
	    StringBuilder sb = new StringBuilder();
	    for (int i = 0; i < baseParam.size(); i++) {
	        String s = baseParam.get(i);
	        if (i == 0) {
	            sb.append(s);
	        } else {
	            sb.append("[" + s + "]");
	        }
	    }
	    return sb.toString();
	}

	/**
	 * Check if String is either empty or null
	 *
	 * @param str string to check
	 * @return true if string is empty. Else return false
	 */
	public static boolean isEmpty(String str) {
	    return str == null || str.length() == 0;
	}


	@SuppressWarnings("deprecation")
	private static String encodeParam(Object param) {
	    try {
	        return URLEncoder.encode(String.valueOf(param), "UTF-8");
	    } catch (UnsupportedEncodingException e) {
	        return URLEncoder.encode(String.valueOf(param));
	    }
	}
}
