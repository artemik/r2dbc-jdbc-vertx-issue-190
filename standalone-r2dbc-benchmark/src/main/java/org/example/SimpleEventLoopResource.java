package org.example;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import reactor.netty.resources.LoopResources;

import java.util.concurrent.ThreadFactory;

import static java.lang.Math.max;

public class SimpleEventLoopResource implements LoopResources {

    private static Integer DEFAULT_ACCEPTOR_THREADS = 1;
    private final String prefix = "r2dbc";

    public SimpleEventLoopResource() {

    }
    public SimpleEventLoopResource(Integer thread) {
        if (thread != null) {
            DEFAULT_ACCEPTOR_THREADS = thread;
        }
    }

    @Override
    public EventLoopGroup onServerSelect(boolean useNative) {
        ThreadFactory threadFactory = new DefaultThreadFactory(prefix + "-acceptor", useNative);
        return new NioEventLoopGroup(DEFAULT_ACCEPTOR_THREADS, threadFactory);
    }

    @Override
    public EventLoopGroup onServer(boolean useNative) {
        ThreadFactory threadFactory = new DefaultThreadFactory(prefix + "-worker", useNative);
        int processor = Runtime.getRuntime().availableProcessors();
        return new NioEventLoopGroup(max(processor, DEFAULT_ACCEPTOR_THREADS), threadFactory);
    }

}
