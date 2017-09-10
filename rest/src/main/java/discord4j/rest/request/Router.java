package discord4j.rest.request;

import discord4j.rest.http.client.SimpleHttpClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;
import reactor.util.function.Tuples;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Facilitates the routing of {@link discord4j.rest.request.DiscordRequest DiscordRequests} to the proper
 * {@link discord4j.rest.request.RequestStream RequestStream} according to the bucket in which the request falls.
 */
public class Router {

	private final SimpleHttpClient httpClient;
	private final GlobalRateLimiter globalRateLimiter = new GlobalRateLimiter();
	private final Map<BucketKey, RequestStream<?>> streamMap = new ConcurrentHashMap<>();

	public Router(SimpleHttpClient httpClient) {
		this.httpClient = httpClient;
	}

	/**
	 * Queues a request for execution in the appropriate {@link discord4j.rest.request.RequestStream request stream}
	 * according to the request's {@link discord4j.rest.request.BucketKey bucket}.
	 *
	 * @param request The request to queue.
	 * @param <T> The request's response type.
	 * @return A mono that receives signals based on the request's response.
	 */
	public <T> Mono<T> exchange(DiscordRequest<T> request) {
		return Mono.defer(() -> {
			RequestStream<T> stream = getStream(request);
			MonoProcessor<T> callback = MonoProcessor.create();

			stream.push(Tuples.of(callback, request));
			return callback;
		});
	}

	@SuppressWarnings("unchecked")
	private <T> RequestStream<T> getStream(DiscordRequest<T> request) {
		return (RequestStream<T>)
				streamMap.computeIfAbsent(BucketKey.of(request.getRoute().getUriTemplate(), request.getCompleteUri()),
						k -> {
							RequestStream<T> stream = new RequestStream<>(httpClient, globalRateLimiter);
							stream.start();
							return stream;
						});
	}
}
