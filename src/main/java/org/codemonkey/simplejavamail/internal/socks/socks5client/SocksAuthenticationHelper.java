

package org.codemonkey.simplejavamail.internal.socks.socks5client;

import org.codemonkey.simplejavamail.internal.socks.common.SocksException;
import org.codemonkey.simplejavamail.internal.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

class SocksAuthenticationHelper {
	private static final Logger LOGGER = LoggerFactory.getLogger(SocksAuthenticationHelper.class);

	private static final byte SOCKS_VERSION = 0x05;
	private static final int ACCEPTABLE_METHODS = 2; // anonymous & user / password

	private static final int NO_AUTHENTICATION_REQUIRED_METHOD = 0x00;
	private static final int USERNAME_PASSWORD_METHOD = 0x02;

	/**
	 * Performs an authentication method request to see how the proxy server wants to authenticate. GSSAPI is not supported, only anonymous
	 * and user / password authentication.
	 */
	public static boolean shouldAuthenticate(Socket socket)
			throws IOException {
		// send data
		byte[] bufferSent = new byte[4];
		bufferSent[0] = SOCKS_VERSION;
		bufferSent[1] = (byte) ACCEPTABLE_METHODS;
		bufferSent[2] = (byte) NO_AUTHENTICATION_REQUIRED_METHOD;
		bufferSent[3] = (byte) USERNAME_PASSWORD_METHOD;

		OutputStream outputStream = socket.getOutputStream();
		outputStream.write(bufferSent);
		outputStream.flush();

		LOGGER.debug("{}", Util.buildLogString(bufferSent, false));

		// Received data.
		InputStream inputStream = socket.getInputStream();
		byte[] receivedData = read2Bytes(inputStream);
		LOGGER.debug("{}", Util.buildLogString(receivedData, true));
		if (receivedData[0] != (int) SOCKS_VERSION) {
			throw new SocksException("Remote server don't support SOCKS5");
		}
		byte command = receivedData[1];
		if (command != NO_AUTHENTICATION_REQUIRED_METHOD && command != USERNAME_PASSWORD_METHOD) {
			throw new SocksException("requested authentication method not supported: " + command);
		}
		return command == USERNAME_PASSWORD_METHOD;
	}

	public static void performUserPasswordAuthentication(Socks5 socksProxy)
			throws IOException {
		Util.checkNotNull(socksProxy, "Argument [socksProxy] may not be null");
		ProxyCredentials credentials = socksProxy.getCredentials();
		if (credentials == null) {
			throw new SocksException("Need Username/Password authentication");
		}

		String username = credentials.getUsername();
		String password = credentials.getPassword();
		InputStream inputStream = socksProxy.getInputStream();
		OutputStream outputStream = socksProxy.getOutputStream();

		final int USERNAME_LENGTH = username.getBytes().length;
		final int PASSWORD_LENGTH = password.getBytes().length;
		final byte[] bytesOfUsername = username.getBytes();
		final byte[] bytesOfPassword = password.getBytes();
		final byte[] bufferSent = new byte[3 + USERNAME_LENGTH + PASSWORD_LENGTH];

		bufferSent[0] = 0x01; // VER
		bufferSent[1] = (byte) USERNAME_LENGTH; // ULEN
		System.arraycopy(bytesOfUsername, 0, bufferSent, 2, USERNAME_LENGTH);// UNAME
		bufferSent[2 + USERNAME_LENGTH] = (byte) PASSWORD_LENGTH; // PLEN
		System.arraycopy(bytesOfPassword, 0, bufferSent, 3 + USERNAME_LENGTH, PASSWORD_LENGTH); // PASSWD
		outputStream.write(bufferSent);
		outputStream.flush();
		// logger send bytes
		LOGGER.debug("{}", Util.buildLogString(bufferSent, false));

		byte[] authenticationResult = new byte[2];
		//noinspection ResultOfMethodCallIgnored
		inputStream.read(authenticationResult);
		// logger
		LOGGER.debug("{}", Util.buildLogString(authenticationResult, true));

		if (authenticationResult[1] != Socks5.AUTHENTICATION_SUCCEEDED) {
			// Close connection if authentication is failed.
			outputStream.close();
			inputStream.close();
			socksProxy.getProxySocket().close();
			throw new SocksException("Username or password error");
		}
	}

	private static byte[] read2Bytes(InputStream inputStream)
			throws IOException {
		byte[] bytes = new byte[2];
		bytes[0] = (byte) checkEnd(inputStream.read());
		bytes[1] = (byte) checkEnd(inputStream.read());
		return bytes;
	}

	private static int checkEnd(int b)
			throws IOException {
		if (b < 0) {
			throw new IOException("End of stream");
		} else {
			return b;
		}
	}

}