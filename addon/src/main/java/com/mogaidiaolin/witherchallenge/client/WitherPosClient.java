package com.mogaidiaolin.witherchallenge.client;

import com.mogaidiaolin.witherchallenge.net.WitherPosMessage;

/**
 * 客户端缓存：最近一次收到的「最近凋灵」信息（由服务端每 2 秒推一次）。
 */
public final class WitherPosClient {

    private static WitherPosMessage latest = null;

    public static void update(WitherPosMessage msg) {
        latest = msg;
    }

    public static WitherPosMessage latest() {
        return latest;
    }

    private WitherPosClient() {
    }
}
