package com.xyron.game.launcher.fragments;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.xyron.game.R;
import com.xyron.game.launcher.MainActivity;
import com.xyron.game.launcher.util.ButtonAnimator;
import com.xyron.game.launcher.util.SampQueryApi;
import com.xyron.game.launcher.util.ServerConfigManager;
import com.xyron.game.launcher.util.Util;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ServersFragment extends Fragment {
    private static final long INFO_CACHE_TTL_MS = 3000L;
    private static final long AUTO_REFRESH_INTERVAL_MS = 2500L;

    private final ConcurrentHashMap<String, ServerLiveInfo> serverInfoCache = new ConcurrentHashMap<>();
    private final Set<String> activeRequests = ConcurrentHashMap.newKeySet();
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTicker = new Runnable() {
        @Override
        public void run() {
            if (!isAdded()) {
                return;
            }
            refreshContent();
            refreshHandler.postDelayed(this, AUTO_REFRESH_INTERVAL_MS);
        }
    };

    private TextView emptyState;
    private TextView addServerShortcut;
    private LinearLayout serverListContainer;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_servers, container, false);

        emptyState = root.findViewById(R.id.server_empty_state);
        addServerShortcut = root.findViewById(R.id.server_add_shortcut);
        serverListContainer = root.findViewById(R.id.server_list_container);

        if (addServerShortcut != null) {
            addServerShortcut.setOnTouchListener(new ButtonAnimator(requireContext(), addServerShortcut));
            addServerShortcut.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).openTab(MainActivity.TAB_SETTINGS);
                }
            });
        }

        refreshContent();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshHandler.removeCallbacks(refreshTicker);
        refreshHandler.post(refreshTicker);
    }

    @Override
    public void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshTicker);
    }

    private void refreshContent() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        List<ServerConfigManager.ServerOption> options = ServerConfigManager.getAvailableServers(context);
        pruneCache(options);
        ServerConfigManager.ServerOption selectedServer = ServerConfigManager.getSelectedServer(context);
        renderList(options, selectedServer);
    }

    private void pruneCache(List<ServerConfigManager.ServerOption> options) {
        HashSet<String> validKeys = new HashSet<>();
        for (ServerConfigManager.ServerOption option : options) {
            validKeys.add(option.getAddress());
        }

        for (String key : new HashSet<>(serverInfoCache.keySet())) {
            if (!validKeys.contains(key)) {
                serverInfoCache.remove(key);
                activeRequests.remove(key);
            }
        }
    }

    private void renderList(List<ServerConfigManager.ServerOption> options,
                            ServerConfigManager.ServerOption selectedServer) {
        Context context = getContext();
        if (context == null || serverListContainer == null || emptyState == null) {
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(context);
        serverListContainer.removeAllViews();

        if (options.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            if (addServerShortcut != null) {
                addServerShortcut.setVisibility(View.VISIBLE);
            }
            return;
        }

        emptyState.setVisibility(View.GONE);
        if (addServerShortcut != null) {
            addServerShortcut.setVisibility(View.GONE);
        }

        for (ServerConfigManager.ServerOption option : options) {
            View itemView = inflater.inflate(R.layout.item_server_selector, serverListContainer, false);
            bindServerItem(context, itemView, option, selectedServer);
            serverListContainer.addView(itemView);
        }
    }

    private void bindServerItem(Context context,
                                View itemView,
                                ServerConfigManager.ServerOption option,
                                ServerConfigManager.ServerOption selectedServer) {
        TextView nameView = itemView.findViewById(R.id.item_server_name);
        TextView addressView = itemView.findViewById(R.id.item_server_address);
        TextView summaryView = itemView.findViewById(R.id.item_server_players);
        TextView metaView = itemView.findViewById(R.id.item_server_meta);
        TextView selectAction = itemView.findViewById(R.id.item_server_select_action);
        TextView removeAction = itemView.findViewById(R.id.item_server_remove_action);

        boolean isSelected = selectedServer != null
                && selectedServer.isValid()
                && option.matches(selectedServer.host, selectedServer.port);

        ServerLiveInfo liveInfo = serverInfoCache.get(option.getAddress());
        nameView.setText(buildServerTitle(option, liveInfo));
        summaryView.setText(buildSummary(isSelected, liveInfo));
        metaView.setText(buildMeta(liveInfo));
        addressView.setText(option.getAddress());

        selectAction.setText(isSelected ? "Ativo" : "Selecionar");
        selectAction.setAlpha(isSelected ? 0.92f : 1.0f);
        selectAction.setOnTouchListener(new ButtonAnimator(context, selectAction));
        selectAction.setOnClickListener(v -> {
            boolean saved = ServerConfigManager.saveSelectedServer(context, option);
            if (!saved) {
                Toast.makeText(context, "Nao foi possivel selecionar o servidor.", Toast.LENGTH_SHORT).show();
                return;
            }

            refreshContent();
            Toast.makeText(context, "Servidor ativo: " + option.getAddress(), Toast.LENGTH_SHORT).show();
        });

        removeAction.setOnTouchListener(new ButtonAnimator(context, removeAction));
        removeAction.setOnClickListener(v -> {
            boolean removed = ServerConfigManager.removeServer(context, option);
            if (!removed) {
                Toast.makeText(context, "Nao foi possivel remover o servidor.", Toast.LENGTH_SHORT).show();
                return;
            }

            serverInfoCache.remove(option.getAddress());
            activeRequests.remove(option.getAddress());
            refreshContent();
            Toast.makeText(context, "Servidor removido: " + option.getAddress(), Toast.LENGTH_SHORT).show();
        });

        applyItemStyle(itemView, isSelected, liveInfo);

        if (shouldRefresh(liveInfo)) {
            requestServerDetails(option);
        }
    }

    private boolean shouldRefresh(ServerLiveInfo info) {
        return info == null || (System.currentTimeMillis() - info.fetchedAtMs) > INFO_CACHE_TTL_MS;
    }

    private void requestServerDetails(ServerConfigManager.ServerOption option) {
        final String cacheKey = option.getAddress();
        if (!activeRequests.add(cacheKey)) {
            return;
        }

        new Thread(() -> {
            ServerLiveInfo liveInfo = queryServer(option);
            serverInfoCache.put(cacheKey, liveInfo);
            activeRequests.remove(cacheKey);

            if (!isAdded()) {
                return;
            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(this::refreshContent);
            }
        }, "server-query-" + option.port).start();
    }

    private ServerLiveInfo queryServer(ServerConfigManager.ServerOption option) {
        SampQueryApi queryApi = new SampQueryApi(option.host, option.port, 550);
        try {
            long pingValue = queryApi.measurePingMs(1);
            if (pingValue < 0) {
                return ServerLiveInfo.offline();
            }

            String[] info = queryApi.getInfo();
            int ping = pingValue < 0 ? 0 : (int) pingValue;

            if (info == null) {
                return ServerLiveInfo.online("", "", "", 0, 0, ping);
            }

            int players = parseSafeInt(info[1]);
            int maxPlayers = parseSafeInt(info[2]);
            String serverName = cleanValue(info[3]);
            String gameMode = cleanValue(info[4]);
            String language = cleanValue(info[5]);

            return ServerLiveInfo.online(serverName, gameMode, language, players, maxPlayers, ping);
        } catch (Exception ignored) {
            return ServerLiveInfo.offline();
        } finally {
            queryApi.close();
        }
    }

    private int parseSafeInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String cleanValue(String value) {
        if (value == null) {
            return "";
        }
        return Util.getStringWithoutColors(value).trim();
    }

    private String buildServerTitle(ServerConfigManager.ServerOption option, ServerLiveInfo info) {
        if (info == null || !info.online || info.serverName.isEmpty()) {
            return option.getAddress();
        }
        return info.serverName;
    }

    private String buildSummary(boolean isSelected, ServerLiveInfo info) {
        if (info == null) {
            return isSelected
                    ? "Ativo no jogo | consultando servidor..."
                    : "Consultando status, ping e jogadores...";
        }

        if (!info.online) {
            return isSelected
                    ? "Ativo no jogo | offline no momento"
                    : "Offline | sem resposta ao query";
        }

        StringBuilder summary = new StringBuilder();
        if (isSelected) {
            summary.append("Ativo | ");
        }
        summary.append("Online");
        summary.append(" | ").append(info.currentPlayers).append("/").append(info.maxPlayers);
        if (info.ping > 0) {
            summary.append(" | ").append(info.ping).append(" ms");
        }
        return summary.toString();
    }

    private String buildMeta(ServerLiveInfo info) {
        if (info == null) {
            return "Buscando nome real, modo de jogo e linguagem...";
        }

        if (!info.online) {
            return "O nome, o modo e a linguagem aparecem quando o servidor responder.";
        }

        if (info.serverName.isEmpty() && info.gameMode.isEmpty() && info.language.isEmpty()) {
            return "Servidor respondeu ao ping, mas nao enviou detalhes de nome, modo ou linguagem.";
        }

        String modeType = classifyMode(info.gameMode, info.serverName);
        String language = info.language.isEmpty() ? "Unknown" : info.language;

        if (info.gameMode.isEmpty()) {
            return modeType + " | " + language;
        }
        return modeType + " | " + info.gameMode + " | " + language;
    }

    private String classifyMode(String gameMode, String serverName) {
        String haystack = (gameMode + " " + serverName).toLowerCase();
        if (haystack.contains("roleplay") || haystack.contains("role play") || haystack.contains(" rp")) {
            return "RP";
        }
        return "Nao RP";
    }

    private void applyItemStyle(View itemView, boolean isSelected, ServerLiveInfo info) {
        View statusView = itemView.findViewById(R.id.item_server_status);
        TextView summaryView = itemView.findViewById(R.id.item_server_players);
        TextView metaView = itemView.findViewById(R.id.item_server_meta);

        itemView.setBackgroundResource(isSelected
                ? R.drawable.home_server_option_selected
                : R.drawable.home_server_option_default);

        if (statusView != null) {
            if (info == null) {
                statusView.setBackgroundResource(R.drawable.server_status_dot_loading);
            } else if (info.online) {
                statusView.setBackgroundResource(R.drawable.server_status_dot_online);
            } else {
                statusView.setBackgroundResource(R.drawable.server_status_dot_offline);
            }
        }

        if (summaryView != null) {
            if (info == null) {
                summaryView.setTextColor(Color.parseColor("#D3D9DF"));
            } else if (info.online) {
                summaryView.setTextColor(Color.parseColor("#76E6B4"));
            } else {
                summaryView.setTextColor(Color.parseColor("#FF7B8D"));
            }
        }

        if (metaView != null) {
            metaView.setTextColor(Color.parseColor(isSelected ? "#F2DEB2" : "#95A6B5"));
        }
    }

    private static final class ServerLiveInfo {
        final boolean online;
        final String serverName;
        final String gameMode;
        final String language;
        final int currentPlayers;
        final int maxPlayers;
        final int ping;
        final long fetchedAtMs;

        private ServerLiveInfo(boolean online,
                               String serverName,
                               String gameMode,
                               String language,
                               int currentPlayers,
                               int maxPlayers,
                               int ping,
                               long fetchedAtMs) {
            this.online = online;
            this.serverName = serverName == null ? "" : serverName;
            this.gameMode = gameMode == null ? "" : gameMode;
            this.language = language == null ? "" : language;
            this.currentPlayers = currentPlayers;
            this.maxPlayers = maxPlayers;
            this.ping = ping;
            this.fetchedAtMs = fetchedAtMs;
        }

        static ServerLiveInfo online(String serverName,
                                     String gameMode,
                                     String language,
                                     int currentPlayers,
                                     int maxPlayers,
                                     int ping) {
            return new ServerLiveInfo(
                    true,
                    serverName,
                    gameMode,
                    language,
                    currentPlayers,
                    maxPlayers,
                    ping,
                    System.currentTimeMillis()
            );
        }

        static ServerLiveInfo offline() {
            return new ServerLiveInfo(
                    false,
                    "",
                    "",
                    "",
                    0,
                    0,
                    0,
                    System.currentTimeMillis()
            );
        }
    }
}
