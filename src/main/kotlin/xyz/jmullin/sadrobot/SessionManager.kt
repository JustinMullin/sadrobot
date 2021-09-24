/*
 * Sad Robot MTG Card Fetcher
 * Copyright (C) 2021 Justin Mullin
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.jmullin.sadrobot

import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object SessionManager {
    private val logger: Logger = LoggerFactory.getLogger(SessionManager::class.java)

    fun startupWorkspace(workspace: Workspace) {
        logger.info("Starting session for workspace '${workspace.name}', type '${workspace.type}'")
        when (workspace.type) {
            "Slack" -> startSlackSession(workspace)
            "Discord" -> startDiscordSession(workspace)
        }
    }

    private fun startSlackSession(workspace: Workspace) {
        SlackListener(workspace).startSession()
    }

    private fun startDiscordSession(workspace: Workspace) {
        JDABuilder(workspace.botToken)
            .addEventListeners(DiscordListener(workspace))
            .setActivity(Activity.listening("for your MTG card queries."))
            .build()
    }
}
