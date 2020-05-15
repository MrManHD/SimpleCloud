package eu.thesimplecloud.base.manager.commands.provider

import eu.thesimplecloud.api.CloudAPI
import eu.thesimplecloud.api.command.ICommandSender
import eu.thesimplecloud.api.player.ICloudPlayer
import eu.thesimplecloud.api.servicegroup.ICloudServiceGroup
import eu.thesimplecloud.api.utils.getAllFieldsFromClassAndSubClasses
import eu.thesimplecloud.launcher.console.command.provider.ICommandSuggestionProvider

/**
 * Created by IntelliJ IDEA.
 * User: Philipp.Eistrach
 * Date: 14.04.2020
 * Time: 18:39
 */
class EditGroupParameterCommandSuggestionProvider: ICommandSuggestionProvider {

    override fun getSuggestions(sender: ICommandSender, lastArgument: String): List<String> {
        val allFields = ICloudServiceGroup::class.java.getAllFieldsFromClassAndSubClasses().filter { !Collection::class.java.isAssignableFrom(it.type) }
        return allFields.filterNot { it.name == "name" || it.name == "serviceVersion" }.map { it.name }
    }

}