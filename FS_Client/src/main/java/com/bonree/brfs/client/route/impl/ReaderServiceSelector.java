package com.bonree.brfs.client.route.impl;

import java.util.List;
import java.util.Random;

import com.bonree.brfs.client.meta.ServiceMetaCache;
import com.bonree.brfs.client.route.RouteParser;
import com.bonree.brfs.client.route.ServiceSelector_1;
import com.bonree.brfs.common.service.Service;
import com.google.common.base.Preconditions;

public class ReaderServiceSelector implements ServiceSelector_1 {

    private final static String NAME_SEPARATOR = "_";

    @Override
    public Service selectService(ServiceMetaCache serviceCache,RouteParser routeParser,String partFid) {
        Preconditions.checkNotNull(partFid);
        List<String> aliveServices = serviceCache.listSecondID();
        Service service = null;
        String[] arrs = partFid.split(NAME_SEPARATOR);
        int paras = arrs.length;
        if (paras == 2) { // 一个副本
            service = serviceCache.getFirstServerCache(arrs[1]);
        } else if (paras > 2) {// 多个副本时，选择一个副本
            int replicas = arrs.length - 1; // 除去UUID
            int random = new Random().nextInt(replicas);
            String selectSId = arrs[random + 1];
            String aliveSecondID = routeParser.findServerID(selectSId, partFid, NAME_SEPARATOR, aliveServices);
            if (aliveSecondID != null) {
                service = serviceCache.getSecondServerCache(aliveSecondID);
            } else {
                for (int i = 0; i < replicas - 1; i++) {
                    random = (random + 1) % replicas; // 尝试选择下一个service
                    selectSId = arrs[random + 1];
                    aliveSecondID = routeParser.findServerID(selectSId, partFid, NAME_SEPARATOR, aliveServices);
                    if (aliveSecondID != null) {
                        service = serviceCache.getSecondServerCache(aliveSecondID);
                        break;
                    }
                }
            }
        }
        return service;
    }

}