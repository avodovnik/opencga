/*
 * Copyright 2015-2017 OpenCB
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

package org.opencb.opencga.app.cli.main.options;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;
import org.opencb.opencga.app.cli.GeneralCliOptions;
import org.opencb.opencga.app.cli.main.options.commons.AclCommandOptions;
import org.opencb.opencga.app.cli.main.options.commons.AnnotationCommandOptions;

/**
 * Created by pfurio on 15/05/17.
 */
@Parameters(commandNames = {"families"}, commandDescription = "Family commands")
public class FamilyCommandOptions {

//    public CreateCommandOptions createCommandOptions;
    public InfoCommandOptions infoCommandOptions;
    public SearchCommandOptions searchCommandOptions;
    public GroupByCommandOptions groupByCommandOptions;
    public StatsCommandOptions statsCommandOptions;
//    public UpdateCommandOptions updateCommandOptions;

    public AclCommandOptions.AclsCommandOptions aclsCommandOptions;
    public AclCommandOptions.AclsUpdateCommandOptions aclsUpdateCommandOptions;

    public AnnotationCommandOptions.AnnotationSetsCreateCommandOptions annotationCreateCommandOptions;
    public AnnotationCommandOptions.AnnotationSetsSearchCommandOptions annotationSearchCommandOptions;
    public AnnotationCommandOptions.AnnotationSetsDeleteCommandOptions annotationDeleteCommandOptions;
    public AnnotationCommandOptions.AnnotationSetsInfoCommandOptions annotationInfoCommandOptions;
    public AnnotationCommandOptions.AnnotationSetsUpdateCommandOptions annotationUpdateCommandOptions;

    public JCommander jCommander;
    public GeneralCliOptions.CommonCommandOptions commonCommandOptions;
    public GeneralCliOptions.DataModelOptions commonDataModelOptions;
    public GeneralCliOptions.NumericOptions commonNumericOptions;

    public FamilyCommandOptions(GeneralCliOptions.CommonCommandOptions commonCommandOptions, GeneralCliOptions.DataModelOptions dataModelOptions,
                                    GeneralCliOptions.NumericOptions numericOptions, JCommander jCommander) {

        this.commonCommandOptions = commonCommandOptions;
        this.commonDataModelOptions = dataModelOptions;
        this.commonNumericOptions = numericOptions;
        this.jCommander = jCommander;

//        this.createCommandOptions = new CreateCommandOptions();
        this.infoCommandOptions = new InfoCommandOptions();
        this.searchCommandOptions = new SearchCommandOptions();
        this.groupByCommandOptions = new GroupByCommandOptions();
        this.statsCommandOptions = new StatsCommandOptions();
//        this.updateCommandOptions = new UpdateCommandOptions();

        AnnotationCommandOptions annotationCommandOptions = new AnnotationCommandOptions(commonCommandOptions);
        this.annotationCreateCommandOptions = annotationCommandOptions.getCreateCommandOptions();
        this.annotationSearchCommandOptions = annotationCommandOptions.getSearchCommandOptions();
        this.annotationDeleteCommandOptions = annotationCommandOptions.getDeleteCommandOptions();
        this.annotationInfoCommandOptions = annotationCommandOptions.getInfoCommandOptions();
        this.annotationUpdateCommandOptions = annotationCommandOptions.getUpdateCommandOptions();

        AclCommandOptions aclCommandOptions = new AclCommandOptions(commonCommandOptions);
        this.aclsCommandOptions = aclCommandOptions.getAclsCommandOptions();
        this.aclsUpdateCommandOptions = aclCommandOptions.getAclsUpdateCommandOptions();
    }

    public class BaseFamilyCommand extends GeneralCliOptions.StudyOption {

        @ParametersDelegate
        public GeneralCliOptions.CommonCommandOptions commonOptions = commonCommandOptions;

        @Parameter(names = {"--family"}, description = "Family id or name", required = true, arity = 1)
        public String family;

    }

//    @Parameters(commandNames = {"create"}, commandDescription = "Create family.")
//    public class CreateCommandOptions extends GeneralCliOptions.StudyOption {
//
//        @ParametersDelegate
//        public GeneralCliOptions.CommonCommandOptions commonOptions = commonCommandOptions;
//
//        @Parameter(names = {"-n", "--name"}, description = "Family name", required = true, arity = 1)
//        public String name;
//
//        @Parameter(names = {"--father"}, description = "Father name", arity = 1)
//        public String father;
//
//        @Parameter(names = {"--mother"}, description = "Mother name", arity = 1)
//        public String mother;
//
//        @Parameter(names = {"--member"}, description = "Comma separated list of child names", arity = 1)
//        public String member;
//
//        @Parameter(names = {"--description"}, description = "Description of the family", arity = 1)
//        public String description;
//
//        @Parameter(names = {"--parental-consanguinity"}, description = "Flag indicating if the parents descend from the same ancestor", arity = 0)
//        public boolean parentalConsanguinity;
//    }

    @Parameters(commandNames = {"info"}, commandDescription = "Get family information")
    public class InfoCommandOptions extends BaseFamilyCommand {

        @ParametersDelegate
        public GeneralCliOptions.DataModelOptions dataModelOptions = commonDataModelOptions;

    }

    @Parameters(commandNames = {"search"}, commandDescription = "Search for families")
    public class SearchCommandOptions extends GeneralCliOptions.StudyOption {

        @ParametersDelegate
        public GeneralCliOptions.CommonCommandOptions commonOptions = commonCommandOptions;

        @ParametersDelegate
        public GeneralCliOptions.DataModelOptions dataModelOptions = commonDataModelOptions;

        @ParametersDelegate
        public GeneralCliOptions.NumericOptions numericOptions = commonNumericOptions;

        @Parameter(names = {"--name"}, description = "name", arity = 1)
        public String name;

        @Parameter(names = {"--members"}, description = "Comma separated list of individual ids or names", arity = 1)
        public String members;

        @Parameter(names = {"--parental-consanguinity"}, description = "Parental consanguinity", arity = 1)
        public Boolean parentalConsanguinity;

        @Parameter(names = {"--annotation"}, description = "Annotation, e.g: key1=value(,key2=value)", arity = 1)
        public String annotation;
    }

    @Parameters(commandNames = {"group-by"}, commandDescription = "Group families")
    public class GroupByCommandOptions extends GeneralCliOptions.StudyOption {

        @ParametersDelegate
        public GeneralCliOptions.CommonCommandOptions commonOptions = commonCommandOptions;

        @Parameter(names = {"-f", "--fields"}, description = "Comma separated list of fields by which to group by.", required = true, arity = 1)
        public String fields;

        @Parameter(names = {"-n", "--name"}, description = "Comma separated list of names.", required = false, arity = 0)
        public String name;

        @Parameter(names = {"--annotation"}, description = "Annotation, e.g: key1=value(,key2=value)", required = false, arity = 1)
        public String annotation;

        @Parameter(names = {"--annotation-set-name"}, description = "Annotation set name.", required = false, arity = 0)
        public String annotationSetName;

        @Parameter(names = {"--variable-set"}, description = "Variable set ids", required = false, arity = 1)
        public String variableSetId;
    }

    @Parameters(commandNames = {"stats"}, commandDescription = "Family stats")
    public class StatsCommandOptions extends GeneralCliOptions.StudyOption {

        @ParametersDelegate
        public GeneralCliOptions.CommonCommandOptions commonOptions = commonCommandOptions;

        @Parameter(names = {"--default"}, description = "Flag to calculate default stats", arity = 0)
        public boolean defaultStats;

        @Parameter(names = {"--creation-year"}, description = "Creation year.", arity = 1)
        public String creationYear;

        @Parameter(names = {"--creation-month"}, description = "Creation month (JANUARY, FEBRUARY...).", arity = 1)
        public String creationMonth;

        @Parameter(names = {"--creation-day"}, description = "Creation day.", arity = 1)
        public String creationDay;

        @Parameter(names = {"--creation-day-of-week"}, description = "Creation day of week (MONDAY, TUESDAY...).", arity = 1)
        public String creationDayOfWeek;

        @Parameter(names = {"--phenotypes"}, description = "Phenotypes.", arity = 1)
        public String phenotypes;

        @Parameter(names = {"--status"}, description = "Status.", arity = 1)
        public String status;

        @Parameter(names = {"--release"}, description = "Release.", arity = 1)
        public String release;

        @Parameter(names = {"--version"}, description = "Version.", arity = 1)
        public String version;

        @Parameter(names = {"--num-members"}, description = "Number of members.", arity = 1)
        public String numMembers;

        @Parameter(names = {"--expected-size"}, description = "Expected size.", arity = 1)
        public String expectedSize;

        @Parameter(names = {"--annotation"}, description = "Annotation. See documentation to see the options.", arity = 1)
        public String annotation;

        @Parameter(names = {"--field"}, description = "List of fields separated by semicolons, e.g.: studies;type. For nested "
                + "fields use >>, e.g.: studies>>biotype;type;numSamples[0..10]:1.", arity = 1)
        public String field;
    }

//    @Parameters(commandNames = {"update"}, commandDescription = "Update family information")
//    public class UpdateCommandOptions extends BaseFamilyCommand {
//
//        @Parameter(names = {"--name"}, description = "New name", arity = 1)
//        public String name;
//
//        @Parameter(names = {"--father-id"}, description = "Father id", arity = 1)
//        public String fatherId;
//
//        @Parameter(names = {"--mother-id"}, description = "Mother id", arity = 1)
//        public String motherId;
//
//        @Parameter(names = {"--member"}, description = "Comma separated list of member ids", arity = 1)
//        public String children;
//
//        @Parameter(names = {"--parental-consanguinity"}, description = "Parental consanguinity", arity = 1)
//        public Boolean parentalConsanguinity;
//
//        @Parameter(names = {"--description"}, description = "Description of the family", arity = 1)
//        public String description;
//    }
    
}
