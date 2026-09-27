package ru.gits.api.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Client address for rate limiting and pseudonymised logging. Behind nginx, Tomcat's RemoteIpValve
 * ({@code server.forward-headers-strategy: native}) has already replaced the proxy address.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
