/*
 * MIT License
 *
 * Copyright (C) 2020 The SimpleCloud authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package eu.thesimplecloud.module.sign.service

import eu.thesimplecloud.api.CloudAPI
import eu.thesimplecloud.api.service.ICloudService
import eu.thesimplecloud.api.service.ServiceState
import eu.thesimplecloud.api.servicegroup.ICloudServiceGroup
import eu.thesimplecloud.module.sign.lib.CloudSign
import eu.thesimplecloud.module.sign.lib.SignLayout
import eu.thesimplecloud.module.sign.lib.SignModuleConfig
import eu.thesimplecloud.plugin.extension.toBukkitLocation
import org.bukkit.block.Sign

class BukkitCloudSign(
        val cloudSign: CloudSign
) {

    val serviceGroup = CloudAPI.instance.getCloudServiceGroupManager().getServiceGroupByName(cloudSign.forGroup)
    private val location = cloudSign.templateLocation.toBukkitLocation()
    val templateLocation = cloudSign.templateLocation
    var currentServer: ICloudService? = null

    private val placeholders = listOf<Placeholder<ICloudService>>(
            Placeholder("SERVICE") { it.getName() },
            Placeholder("ONLINE_PLAYERS") { it.getOnlineCount().toString() },
            Placeholder("ONLINE_COUNT") { it.getOnlineCount().toString() },
            Placeholder("MOTD") { it.getMOTD() },
            Placeholder("HOST") { it.getHost() },
            Placeholder("PORT") { it.getPort().toString() },
            Placeholder("STATE") { it.getState().name },
            Placeholder("NUMBER") { it.getServiceNumber().toString() },
            Placeholder("WRAPPER") { it.getWrapperName()!! }
    )
    private val groupPlaceholders = listOf<Placeholder<ICloudServiceGroup>>(
            Placeholder("GROUP") { it.getName() },
            Placeholder("MAX_PLAYERS") { it.getMaxPlayers().toString() },
            Placeholder("TEMPLATE") { it.getTemplateName() }
    )

    fun checkForExpiredService() {
        val currentServer = this.currentServer
        if (currentServer != null) {
            if (currentServer.getState() != ServiceState.STARTING && currentServer.getState() != ServiceState.VISIBLE)
                this.currentServer = null
        }
    }

    fun update() {
        if (serviceGroup == null) {
            println("[SimpleCloud-Sign] WARNING: Cannot find group by name: ${cloudSign.forGroup}")
            return
        }
        if (location == null) {
            println("[SimpleCloud-Sign] WARNING: Cannot find world by name: ${cloudSign.templateLocation.worldName}")
            return
        }
        if (location.block.state !is Sign) {
            return
        }
        val currentServer = this.currentServer
        val sign = location.block.state as Sign
        clearSign(false)
        val signConfig = SignModuleConfig.INSTANCE.getValue()
        val signLayout = when {
            serviceGroup.isInMaintenance() -> signConfig.getMaintenanceLayout()
            currentServer == null -> signConfig.getSearchingLayout()
            currentServer.getState() == ServiceState.STARTING -> signConfig.getStartingLayout()
            else -> signConfig.getSignLayoutByGroupName(cloudSign.forGroup)
                    ?: SignLayout("none", emptyList())
        }

        val currentFrame = signLayout.getCurrentFrame()
        for (i in 0 until 4) {
            sign.setLine(i, replacePlaceholders(currentFrame.lines[i], currentServer))
        }
        sign.update()
    }

    fun clearSign(update: Boolean = true) {
        if (location == null) {
            println("[SimpleCloud-Sign] WARNING: Cannot find world by name: ${cloudSign.templateLocation.worldName}")
            return
        }
        if (location.block.state is Sign) {
            val sign = location.block.state as Sign
            for (i in 0 until 4) {
                sign.setLine(i, "")
            }
            if (update) sign.update()
        }
    }

    private fun replacePlaceholders(lineToReplace: String, currentServer: ICloudService?): String {
        var lineToReplace = lineToReplace
        if (currentServer != null)
            placeholders.forEach { lineToReplace = it.replacePlaceholder(currentServer, lineToReplace) }
        groupPlaceholders.forEach { lineToReplace = it.replacePlaceholder(this.serviceGroup!!, lineToReplace) }
        return lineToReplace
    }

}