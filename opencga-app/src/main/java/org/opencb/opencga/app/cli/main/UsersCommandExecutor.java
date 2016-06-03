/*
 * Copyright 2015 OpenCB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencb.opencga.app.cli.main;


import org.opencb.opencga.catalog.exceptions.CatalogException;

import java.io.IOException;

/**
 * Created by imedina on 02/03/15.
 */
public class UsersCommandExecutor extends OpencgaCommandExecutor {

    private OpencgaCliOptionsParser.UsersCommandOptions usersCommandOptions;

    public UsersCommandExecutor(OpencgaCliOptionsParser.UsersCommandOptions usersCommandOptions) {
        super(usersCommandOptions.commonOptions);
        this.usersCommandOptions = usersCommandOptions;
    }



    @Override
    public void execute() throws Exception {
        logger.debug("Executing variant command line");

        String subCommandString = usersCommandOptions.getParsedSubCommand();
        switch (subCommandString) {
            case "create":
                create();
                break;
            case "info":
                info();
                break;
            case "list":
                list();
                break;
            case "login":
                login();
                break;
            case "logout":
                logout();
                break;
            default:
                logger.error("Subcommand not valid");
                break;
        }

    }

    private void create() throws CatalogException, IOException {
        logger.debug("Creating user");

    }

    private void info() throws CatalogException {
        logger.debug("User info");
       // logger.debug(openCGAClient.getUserClient().getConfiguration().toString());
    }

    private void list() throws CatalogException {
        logger.debug("List all projects and studies of user");
    }
    private void login() throws CatalogException, IOException {
        logger.debug("Login");
      //  "hgva", "hgva_cafeina", clientConfiguration
        openCGAClient.login("hgva","hgva_cafeina");

    }
    private void logout() throws CatalogException, IOException {
        logger.debug("Logout");
    }

}
