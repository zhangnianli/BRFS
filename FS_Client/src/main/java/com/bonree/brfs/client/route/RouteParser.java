package com.bonree.brfs.client.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bonree.brfs.common.rebalance.Constants;
import com.bonree.brfs.common.rebalance.route.NormalRoute;
import com.bonree.brfs.common.rebalance.route.VirtualRoute;

public class RouteParser {

    private final static Logger LOG = LoggerFactory.getLogger(RouteParser.class);

    private RouteRoleCache routeCache;

    public RouteParser(String zkHosts, int snIndex, String baseRoutePath) {
        routeCache = new RouteRoleCache(zkHosts, snIndex, baseRoutePath);
    }

    public String findServerID(String searchServerID, String fid, String separator, List<String> aliveServers) {

        // fid分为单副本serverID,多副本serverID,虚拟serverID。
        // 单副本不需要查找路由
        // 多副本需要查找路由，查找路由方式不同
        String secondID = null;
        if (Constants.VIRTUAL_ID == searchServerID.charAt(0)) {
            VirtualRoute virtualRoute = routeCache.getVirtualRoute(secondID);
            if (virtualRoute == null) {
                return secondID;
            }
            secondID = virtualRoute.getNewSecondID();
        }
        secondID = searchServerID;
        // 说明该secondID存活，不需要路由查找
        if (aliveServers.contains(secondID)) {
            return secondID;
        }

        // secondID不存活，需要寻找该secondID的存活ID
        NormalRoute routeRole = routeCache.getRouteRole(secondID);
        if (routeRole == null) { // 若没有迁移记录，可能没有迁移完成
            return null;
        }

        // 对文件名进行分割处理
        String[] metaArr = fid.split(separator);
        // 提取出用于hash的部分
        String namePart = metaArr[0];
        // 提取副本数
        int replicas = metaArr.length - 1;

        // 提取出该文件所存储的服务
        List<String> fileServerIds = new ArrayList<>();
        for (int j = 1; j < metaArr.length; j++) {
            // virtual server ID
            if (Constants.VIRTUAL_ID == metaArr[j].charAt(0)) {
                if (metaArr[j].equals(searchServerID)) { // 前面解析过
                    fileServerIds.add(secondID);
                } else { // 需要解析
                    VirtualRoute virtualRoute = routeCache.getVirtualRoute(secondID);
                    if (virtualRoute == null) {
                        LOG.error("gain serverid error!something impossible!!!");
                        return null;
                    }
                    fileServerIds.add(virtualRoute.getNewSecondID());
                }
            }
        }
        // 提取需要查询的serverID的位置
        int serverIDPot = fileServerIds.indexOf(secondID);

        // 这里要判断一个副本是否需要进行迁移
        // 挑选出的可迁移的servers
        String selectMultiId = null;
        // 可获取的server，可能包括自身
        List<String> recoverableServerList = null;
        // 排除掉自身或已有的servers
        List<String> exceptionServerIds = null;
        // 真正可选择的servers
        List<String> selectableServerList = null;

        while (needRecover(fileServerIds, replicas, aliveServers)) {
            for (String deadServer : fileServerIds) {
                if (!aliveServers.contains(deadServer)) {
                    int pot = fileServerIds.indexOf(deadServer);
                    NormalRoute newRoute = routeCache.getRouteRole(deadServer);
                    if (newRoute == null) {
                        return null;
                    }
                    recoverableServerList = newRoute.getNewSecondIDs();
                    exceptionServerIds = new ArrayList<>();
                    exceptionServerIds.addAll(fileServerIds);
                    exceptionServerIds.remove(deadServer);
                    selectableServerList = getSelectedList(recoverableServerList, exceptionServerIds);
                    int index = hashFileName(namePart, selectableServerList.size());
                    selectMultiId = selectableServerList.get(index);
                    fileServerIds.set(pot, selectMultiId);

                    // 判断选取的新节点是否存活
                    if (isAlive(selectMultiId, aliveServers)) {
                        // 判断选取的新节点是否为本节点，该serverID是否在相应的位置
                        if (pot == serverIDPot) {
                            break;
                        }
                    }
                }
            }
        }
        return selectMultiId;
    }

    /** 概述：判断是否需要恢复
     * @param serverIds
     * @param replicaPot
     * @return
     * @user <a href=mailto:weizheng@bonree.com>魏征</a>
     */
    private boolean needRecover(List<String> serverIds, int replicaPot, List<String> aliveServers) {
        boolean flag = false;
        for (int i = 1; i <= serverIds.size(); i++) {
            if (i != replicaPot) {
                if (!aliveServers.contains(serverIds.get(i - 1))) {
                    flag = true;
                    break;
                }
            }
        }
        return flag;
    }

    private List<String> getSelectedList(List<String> aliveServerList, List<String> excludeServers) {
        List<String> selectedList = new ArrayList<>();
        for (String tmp : aliveServerList) {
            if (!excludeServers.contains(tmp)) {
                selectedList.add(tmp);
            }
        }
        Collections.sort(selectedList, new CompareFromName());
        return selectedList;
    }

    static private class CompareFromName implements Comparator<String> {
        @Override
        public int compare(String o1, String o2) {
            return o1.compareTo(o2);
        }
    }

    private int hashFileName(String fileName, int size) {
        int nameSum = sumName(fileName);
        int matchSm = nameSum % size;
        return matchSm;
    }

    private int sumName(String name) {
        int sum = 0;
        for (int i = 0; i < name.length(); i++) {
            sum = sum + name.charAt(i);
        }
        return sum;
    }

    private boolean isAlive(String serverId, List<String> aliveServers) {
        if (aliveServers.contains(serverId)) {
            return true;
        } else {
            return false;
        }
    }

}