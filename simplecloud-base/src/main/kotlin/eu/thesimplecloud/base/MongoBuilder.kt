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

package eu.thesimplecloud.base

class MongoBuilder {

    var host = "localhost"
        private set
    var port = 45678
        private set
    var adminUsername = "adminUsername"
        private set
    var adminPassword = "adminPassowrd"
        private set
    var userDatabase = "cloud"
        private set
    var userName = "cloud"
        private set
    var userPassword = "cloudpassowrd"
        private set
    var directory: String = "database"
        private set

    fun setHost(host: String): MongoBuilder {
        this.host = host
        return this
    }

    fun setPort(port: Int): MongoBuilder {
        this.port = port
        return this
    }

    fun setAdminUserName(adminUsername: String): MongoBuilder {
        this.adminUsername = adminUsername
        return this
    }

    fun setAdminPassword(adminPassword: String): MongoBuilder {
        this.adminPassword = adminPassword
        return this
    }

    fun setDatabase(database: String): MongoBuilder {
        this.userDatabase = database
        return this
    }

    fun setUserName(userName: String): MongoBuilder {
        this.userName = userName
        return this
    }

    fun setUserPassword(userPassword: String): MongoBuilder {
        this.userPassword = userPassword
        return this
    }

    fun setDirectory(directory: String): MongoBuilder {
        this.directory = directory
        return this
    }

}