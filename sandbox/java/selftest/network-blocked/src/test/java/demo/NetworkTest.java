package demo;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;

/** Passes only when the container has no network access. */
class NetworkTest {

    @Test
    void tcpConnectionFails() {
        assertThatThrownBy(() -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("1.1.1.1", 80), 2000);
            }
        }).isInstanceOf(IOException.class);
    }

    @Test
    void dnsLookupFails() {
        assertThatThrownBy(() -> InetAddress.getByName("example.com")).isInstanceOf(IOException.class);
    }

    @Test
    void httpClientFails() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        assertThatThrownBy(() -> client.send(HttpRequest.newBuilder(URI.create("http://1.1.1.1/")).build(),
                HttpResponse.BodyHandlers.discarding())).isInstanceOf(IOException.class);
    }
}
