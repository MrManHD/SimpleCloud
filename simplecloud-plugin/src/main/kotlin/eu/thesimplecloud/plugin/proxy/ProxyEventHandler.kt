package eu.thesimplecloud.plugin.proxy

import eu.thesimplecloud.api.CloudAPI
import eu.thesimplecloud.api.event.player.CloudPlayerDisconnectEvent
import eu.thesimplecloud.api.event.player.CloudPlayerLoginEvent
import eu.thesimplecloud.api.exception.NoSuchPlayerException
import eu.thesimplecloud.api.player.CloudPlayer
import eu.thesimplecloud.api.player.PlayerServerConnectState
import eu.thesimplecloud.api.player.connection.DefaultPlayerConnection
import eu.thesimplecloud.api.service.ICloudService
import eu.thesimplecloud.api.service.ServiceState
import eu.thesimplecloud.clientserverapi.lib.packet.packetsender.sendQuery
import eu.thesimplecloud.plugin.network.packets.PacketOutCreateCloudPlayer
import eu.thesimplecloud.plugin.network.packets.PacketOutGetTabSuggestions
import eu.thesimplecloud.plugin.network.packets.PacketOutPlayerConnectToServer
import eu.thesimplecloud.plugin.network.packets.PacketOutPlayerLoginRequest
import eu.thesimplecloud.plugin.startup.CloudPlugin
import java.util.*

/**
 * Created by IntelliJ IDEA.
 * User: Philipp.Eistrach
 * Date: 15.05.2020
 * Time: 23:07
 */
class ProxyEventHandler() {

    companion object {

        fun handleLogin(playerConnection: DefaultPlayerConnection, cancelEvent: (String) -> Unit) {
            if (!CloudPlugin.instance.communicationClient.isOpen()) {
                cancelEvent("§cProxy still starting.")
                return
            }
            val playerPromise = CloudAPI.instance.getCloudPlayerManager().getCloudPlayer(playerConnection.getUniqueId()).awaitUninterruptibly()
            if (playerPromise.isSuccess) {
                cancelEvent("§cYou are already registered on the network")
                return
            }

            //send login request
            val createPromise = CloudPlugin.instance.communicationClient
                    .sendQuery<CloudPlayer>(PacketOutCreateCloudPlayer(playerConnection, CloudPlugin.instance.thisServiceName), 1000).awaitUninterruptibly()
            if (!createPromise.isSuccess) {
                cancelEvent("§cFailed to create player: ${createPromise.cause().message}")
                throw createPromise.cause()
            }
            val loginRequestPromise = CloudPlugin.instance.communicationClient.sendUnitQuery(PacketOutPlayerLoginRequest(playerConnection.getUniqueId())).awaitUninterruptibly()
            if (!loginRequestPromise.isSuccess) {
                loginRequestPromise.cause().printStackTrace()
                cancelEvent("§cLogin failed: " + loginRequestPromise.cause().message)
            }

            //update player to cache to avoid bugs
            CloudAPI.instance.getCloudPlayerManager().update(createPromise.getNow()!!, true)
        }

        fun handlePostLogin(uniqueId: UUID, name: String) {
            val thisService = CloudPlugin.instance.thisService()
            thisService.setOnlineCount(thisService.getOnlineCount() + 1)
            thisService.update()

            CloudAPI.instance.getEventManager().call(CloudPlayerLoginEvent(uniqueId, name))
        }


        fun handleDisconnect(uniqueId: UUID, name: String) {
            CloudAPI.instance.getEventManager().call(CloudPlayerDisconnectEvent(uniqueId, name))

            val cloudPlayer = CloudAPI.instance.getCloudPlayerManager().getCachedCloudPlayer(uniqueId)
            cloudPlayer?.let {
                cloudPlayer as CloudPlayer
                cloudPlayer.setOffline()
                cloudPlayer.let { CloudAPI.instance.getCloudPlayerManager().delete(it) }
                //send update that the player is now offline
                val client = CloudPlugin.instance.communicationClient
                CloudAPI.instance.getCloudPlayerManager().sendUpdateToConnection(cloudPlayer, client).addCompleteListener {
                    CloudAPI.instance.getCloudPlayerManager().sendDeleteToConnection(cloudPlayer, client)
                }
            }

            subtractOneFromOnlineCount(CloudPlugin.instance.thisService())
        }


        fun handleServerPreConnect(uniqueId: UUID, serverNameFrom: String?, serverNameTo: String, cancelEvent: (String, CancelType) -> Unit) {

            val cloudService = CloudAPI.instance.getCloudServiceManager().getCloudServiceByName(serverNameTo)
            if (cloudService == null) {
                cancelEvent("§cServer is not registered in the cloud.", CancelType.KICK)
                return
            }
            if (cloudService.getServiceGroup().isInMaintenance()) {
                val cloudPlayer = CloudAPI.instance.getCloudPlayerManager().getCachedCloudPlayer(uniqueId)
                if (cloudPlayer == null) {
                    cancelEvent("§cUnable to find cloud player.", CancelType.KICK)
                    return
                }
                if (!cloudPlayer.hasPermissionSync("cloud.maintenance.join")) {
                    cancelEvent("§cThis service is in maintenance.", CancelType.MESSAGE)
                    return
                }
            }

            if (cloudService.getState() == ServiceState.STARTING) {
                cancelEvent("§cServer is still starting.", CancelType.MESSAGE)
                return
            }

            if (serverNameFrom != null) {
                subtractOneFromOnlineCount(serverNameFrom)
            }

            CloudPlugin.instance.communicationClient.sendUnitQuery(PacketOutPlayerConnectToServer(uniqueId, serverNameTo))
                    .awaitUninterruptibly()
                    .addFailureListener {
                        cancelEvent("§cCan't connect to server: " + it.message, CancelType.MESSAGE)
                    }


            //update player
            //use cloned player to compare connected server with old connected server.
            val cloudPlayer = CloudAPI.instance.getCloudPlayerManager().getCachedCloudPlayer(uniqueId) ?: throw NoSuchPlayerException("Cannot find CloudPlayer by uuid: $uniqueId")
            val clonedPlayer = cloudPlayer.clone() as CloudPlayer
            clonedPlayer.setConnectedServerName(cloudService.getName())
            clonedPlayer.setServerConnectState(PlayerServerConnectState.CONNECTING)
            clonedPlayer.update()
        }


        fun handleServerConnect(uniqueId: UUID, serverName: String, cancelEvent: (String) -> Unit) {
            val service = CloudAPI.instance.getCloudServiceManager().getCloudServiceByName(serverName)
            if (service == null) {
                cancelEvent("§cService does not exist.")
                return
            }
            service.setOnlineCount(service.getOnlineCount() + 1)
            service.update()

            val cloudPlayer = CloudAPI.instance.getCloudPlayerManager().getCachedCloudPlayer(uniqueId)
            cloudPlayer ?: return
            val clonedPlayer = cloudPlayer.clone() as CloudPlayer
            clonedPlayer.setServerConnectState(PlayerServerConnectState.CONNECTED)
            clonedPlayer.update()
        }

        fun handleServerKick(kickReasonString: String, serverName: String, cancelEvent: (String, CancelType) -> Unit) {
            if (kickReasonString.isNotEmpty() && kickReasonString.contains("Outdated server") || kickReasonString.contains("Outdated client")) {
                val cloudService = CloudAPI.instance.getCloudServiceManager().getCloudServiceByName(serverName)
                if (cloudService == null || cloudService.isLobby()) {
                    cancelEvent("§cYou are using an unsupported version.", CancelType.KICK)
                    return
                }
            }
        }

        fun handleTabComplete(uuid: UUID, rawCommand: String): Array<String> {
            val commandString = rawCommand.replace("/", "")
            if (commandString.isEmpty()) return emptyArray()

            val suggestions = CloudPlugin.instance.communicationClient.sendQuery<Array<String>>(PacketOutGetTabSuggestions(uuid, commandString)).getBlocking()
            return suggestions
        }

        private fun subtractOneFromOnlineCount(serverName: String) {
            val service = CloudAPI.instance.getCloudServiceManager().getCloudServiceByName(serverName) ?: return
            subtractOneFromOnlineCount(service)
        }

        private fun subtractOneFromOnlineCount(service: ICloudService) {
            service.setOnlineCount(service.getOnlineCount() - 1)
            service.update()
        }

    }


}