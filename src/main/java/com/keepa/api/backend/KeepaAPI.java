package com.keepa.api.backend;

import com.google.gson.stream.JsonReader;
import com.keepa.api.backend.helper.BasicNameFactory;
import com.keepa.api.backend.structs.Request;
import com.keepa.api.backend.structs.Response;
import com.keepa.api.backend.structs.ResponseStatus;
import org.jdeferred.Deferred;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

import static com.keepa.api.backend.helper.Utility.gson;
import static com.keepa.api.backend.helper.Utility.urlEncodeUTF8;

public class KeepaAPI {

	final private String accessKey;
	final private String userAgent;
	final private int maxDelay = 60000;

	/**
	 * @param key     Your private API Access Token
	 */
	public KeepaAPI(String key) {
		this.accessKey = key;
		String apiVersion = getClass().getPackage().getImplementationVersion();
		if (apiVersion != null) {
			userAgent = "KEEPA-JAVA Framework-" + apiVersion;
		} else {
			userAgent = "KEEPA-JAVA Framework-";
		}
	}

	/**
	 * Issue a request to the Keepa Price Data API.
	 * If your tokens are depleted, this method will fail.
	 *
	 * @param r the API Request {@link Request}
	 * @return the API Response {@link Response}
	 */
	public Response sendRequest(Request r) {

		long responseTime = System.nanoTime();
		Response response;

		String query = r.parameter.entrySet().stream()
				.map(p -> urlEncodeUTF8(p.getKey()) + "=" + urlEncodeUTF8(p.getValue()))
				.reduce((p1, p2) -> p1 + "&" + p2)
				.orElse("");

		String url = "https://api.keepa.com/" + r.path + "?key=" + accessKey + "&" + query;

		try {
			URL obj = new URL(url);
			HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();
			con.setUseCaches(false);
			con.setRequestProperty("User-Agent", this.userAgent);
			con.setRequestProperty("Connection", "keep-alive");
			con.setRequestProperty("Accept-Encoding", "gzip");
			con.setConnectTimeout(40000);
			con.setReadTimeout(120000);
			if (r.postData != null) {
				con.setRequestMethod("POST");
				con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
				con.setDoOutput(true);
				OutputStream os = con.getOutputStream();
				os.write(r.postData.getBytes("UTF-8"));
				os.close();
			} else
				con.setRequestMethod("GET");

			int responseCode = con.getResponseCode();

			if (responseCode == 200) {
				try (InputStream is = con.getInputStream();
					 GZIPInputStream gis = new GZIPInputStream(is)) {
					JsonReader reader = new JsonReader(new InputStreamReader(gis, "UTF-8"));
					response = gson.fromJson(reader, Response.class);
					response.status = ResponseStatus.OK;
				} catch (Exception e) {
					response = Response.REQUEST_FAILED;
					e.printStackTrace();
				}
			} else {
				try (InputStream is = con.getErrorStream();
					 GZIPInputStream gis = new GZIPInputStream(is)) {
					JsonReader reader = new JsonReader(new InputStreamReader(gis, "UTF-8"));
					response = gson.fromJson(reader, Response.class);
				} catch (Exception ignored) {
					response = new Response();
				}

				switch (responseCode) {
					case 400:
						response.status = ResponseStatus.REQUEST_REJECTED;
						break;
					case 402:
						response.status = ResponseStatus.PAYMENT_REQUIRED;
						break;
					case 405:
						response.status = ResponseStatus.METHOD_NOT_ALLOWED;
						break;
					case 429:
						response.status = ResponseStatus.NOT_ENOUGH_TOKEN;
						break;
					case 500:
						response.status = ResponseStatus.INTERNAL_SERVER_ERROR;
						break;
					default:
						response = Response.REQUEST_FAILED;
						break;
				}
			}
		} catch (IOException e) {
			response = Response.REQUEST_FAILED;
		}

		response.requestTime = (System.nanoTime() - responseTime) / 1000000;

		return response;
	}

}
