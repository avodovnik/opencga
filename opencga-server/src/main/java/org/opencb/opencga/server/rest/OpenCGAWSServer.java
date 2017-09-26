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

package org.opencb.opencga.server.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Splitter;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.apache.avro.generic.GenericRecord;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;
import org.opencb.biodata.models.alignment.Alignment;
import org.opencb.biodata.models.feature.Genotype;
import org.opencb.biodata.models.variant.VariantSource;
import org.opencb.biodata.models.variant.stats.VariantStats;
import org.opencb.commons.datastore.core.*;
import org.opencb.opencga.catalog.exceptions.CatalogAuthenticationException;
import org.opencb.opencga.catalog.exceptions.CatalogAuthorizationException;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.managers.AbstractManager;
import org.opencb.opencga.catalog.managers.CatalogManager;
import org.opencb.opencga.catalog.utils.Constants;
import org.opencb.opencga.core.models.acls.AclParams;
import org.opencb.opencga.core.common.Config;
import org.opencb.opencga.core.config.Configuration;
import org.opencb.opencga.core.exception.VersionException;
import org.opencb.opencga.storage.core.StorageEngineFactory;
import org.opencb.opencga.storage.core.alignment.json.AlignmentDifferenceJsonMixin;
import org.opencb.opencga.storage.core.config.StorageConfiguration;
import org.opencb.opencga.storage.core.manager.variant.VariantStorageManager;
import org.opencb.opencga.storage.core.variant.io.json.mixin.GenericRecordAvroJsonMixin;
import org.opencb.opencga.storage.core.variant.io.json.mixin.GenotypeJsonMixin;
import org.opencb.opencga.storage.core.variant.io.json.mixin.VariantSourceJsonMixin;
import org.opencb.opencga.storage.core.variant.io.json.mixin.VariantStatsJsonMixin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationPath("/")
@Path("/{version}")
@Produces(MediaType.APPLICATION_JSON)
public class OpenCGAWSServer {

    @DefaultValue("v1")
    @PathParam("version")
    @ApiParam(name = "version", value = "OpenCGA major version", allowableValues = "v1", defaultValue = "v1")
    protected String version;
    protected String exclude;
    protected String include;
    protected int limit;
    protected long skip;
    protected boolean count;
    protected boolean lazy;
    protected String sessionId;

    @DefaultValue("")
    @QueryParam("sid")
    @ApiParam("Session id")
    protected String dummySessionId;

    @HeaderParam("Authorization")
    @DefaultValue("Bearer ")
    @ApiParam("JWT Authentication token")
    protected String authentication;

    protected UriInfo uriInfo;
    protected HttpServletRequest httpServletRequest;
    protected MultivaluedMap<String, String> params;

    protected String sessionIp;

    protected long startTime;

    protected Query query;
    protected QueryOptions queryOptions;

    protected static ObjectWriter jsonObjectWriter;
    protected static ObjectMapper jsonObjectMapper;

    protected static Logger logger; // = LoggerFactory.getLogger(this.getClass());


    protected static AtomicBoolean initialized;

    protected static Configuration configuration;
    protected static CatalogManager catalogManager;

    protected static StorageConfiguration storageConfiguration;
    protected static StorageEngineFactory storageEngineFactory;
    protected static VariantStorageManager variantManager;

    private static final int DEFAULT_LIMIT = 2000;
    private static final int MAX_LIMIT = 5000;

    static {
        initialized = new AtomicBoolean(false);

        jsonObjectMapper = new ObjectMapper();
        jsonObjectMapper.addMixIn(GenericRecord.class, GenericRecordAvroJsonMixin.class);
        jsonObjectMapper.addMixIn(VariantSource.class, VariantSourceJsonMixin.class);
        jsonObjectMapper.addMixIn(VariantStats.class, VariantStatsJsonMixin.class);
        jsonObjectMapper.addMixIn(Genotype.class, GenotypeJsonMixin.class);
        jsonObjectMapper.addMixIn(Alignment.AlignmentDifference.class, AlignmentDifferenceJsonMixin.class);
        jsonObjectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        jsonObjectMapper.configure(MapperFeature.REQUIRE_SETTERS_FOR_GETTERS, true);
        jsonObjectWriter = jsonObjectMapper.writer();


        //Disable MongoDB useless logging
        org.apache.log4j.Logger.getLogger("org.mongodb.driver.cluster").setLevel(Level.WARN);
        org.apache.log4j.Logger.getLogger("org.mongodb.driver.connection").setLevel(Level.WARN);
    }


    public OpenCGAWSServer(@Context UriInfo uriInfo, @Context HttpServletRequest httpServletRequest, @Context HttpHeaders httpHeaders)
            throws IOException, VersionException {
        this(uriInfo.getPathParameters().getFirst("version"), uriInfo, httpServletRequest, httpHeaders);
    }

    public OpenCGAWSServer(@PathParam("version") String version, @Context UriInfo uriInfo, @Context HttpServletRequest httpServletRequest, @Context HttpHeaders httpHeaders)
            throws IOException, VersionException {
        this.version = version;
        this.uriInfo = uriInfo;
        this.httpServletRequest = httpServletRequest;

        this.params = uriInfo.getQueryParameters();

        // This is only executed the first time to initialize configuration and some variables
        if (initialized.compareAndSet(false, true)) {
            init();
        }

        if (catalogManager == null) {
            throw new IllegalStateException("OpenCGA was not properly initialized. Please, check if the configuration files are reachable "
                    + "or properly defined.");
        }

        try {
            verifyHeaders(httpHeaders);
        } catch (CatalogAuthenticationException e) {
            throw new IllegalStateException(e);
        }

        query = new
        Query();

        queryOptions = new
        QueryOptions();

        parseParams();
        // take the time for calculating the whole duration of the call
        startTime = System.currentTimeMillis();
    }

    private void init() {
        logger = LoggerFactory.getLogger("org.opencb.opencga.server.rest.OpenCGAWSServer");
        logger.info("========================================================================");
        logger.info("| Starting OpenCGA REST server, initializing OpenCGAWSServer");
        logger.info("| This message must appear only once.");

        // We must load the configuration files and init catalogManager, storageManagerFactory and Logger only the first time.
        // We first read 'config-dir' parameter passed
        ServletContext context = httpServletRequest.getServletContext();
        String configDirString = context.getInitParameter("config-dir");
        if (StringUtils.isEmpty(configDirString)) {
            // If not environment variable then we check web.xml parameter
            if (StringUtils.isNotEmpty(context.getInitParameter("OPENCGA_HOME"))) {
                configDirString = context.getInitParameter("OPENCGA_HOME") + "/conf";
            } else if (StringUtils.isNotEmpty(System.getenv("OPENCGA_HOME"))) {
                // If not exists then we try the environment variable OPENCGA_HOME
                configDirString = System.getenv("OPENCGA_HOME") + "/conf";
            } else {
                logger.error("No valid configuration directory provided!");
            }
        }

        // Check and execute the init methods
        java.nio.file.Path configDirPath = Paths.get(configDirString);
        if (configDirPath != null && Files.exists(configDirPath) && Files.isDirectory(configDirPath)) {
            logger.info("|  * Configuration folder: '{}'", configDirPath.toString());
            initOpenCGAObjects(configDirPath);

            // Required for reading the analysis.properties file.
            // TODO: Remove when analysis.properties is totally migrated to configuration.yml
            Config.setOpenCGAHome(configDirPath.getParent().toString());

            // TODO use configuration.yml for getting the server.log, for now is hardcoded
            logger.info("|  * Server logfile: " + configDirPath.getParent().resolve("logs").resolve("server.log"));
            initLogger(configDirPath.getParent().resolve("logs"));
        } else {
            logger.error("No valid configuration directory provided: '{}'");
        }

        logger.info("========================================================================\n");
    }

    /**
     * This method loads OpenCGA configuration files and initialize CatalogManager and StorageManagerFactory.
     * This must be only executed once.
     *
     * @param configDir directory containing the configuration files
     */
    private void initOpenCGAObjects(java.nio.file.Path configDir) {
        try {
            logger.info("|  * Catalog configuration file: '{}'", configDir.toFile().getAbsolutePath() + "/configuration.yml");
            configuration = Configuration
                    .load(new FileInputStream(new File(configDir.toFile().getAbsolutePath() + "/configuration.yml")));
            catalogManager = new CatalogManager(configuration);
            // TODO think about this
            if (!catalogManager.existsCatalogDB()) {
//                logger.info("|  * Catalog database created: '{}'", catalogConfiguration.getDatabase().getDatabase());
                logger.info("|  * Catalog database created: '{}'", catalogManager.getCatalogDatabase());
                catalogManager.installCatalogDB();
            }

            logger.info("|  * Storage configuration file: '{}'", configDir.toFile().getAbsolutePath() + "/storage-configuration.yml");
            storageConfiguration = StorageConfiguration
                    .load(new FileInputStream(new File(configDir.toFile().getAbsolutePath() + "/storage-configuration.yml")));
            storageEngineFactory = StorageEngineFactory.get(storageConfiguration);
            variantManager = new VariantStorageManager(catalogManager, storageEngineFactory);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CatalogException e) {
            logger.error("Error while creating CatalogManager", e);
        }
    }

    private void initLogger(java.nio.file.Path logs) {
        try {
            org.apache.log4j.Logger rootLogger = LogManager.getRootLogger();
            PatternLayout layout = new PatternLayout("%d{yyyy-MM-dd HH:mm:ss} [%t] %-5p %c{1}:%L - %m%n");
            String logFile = logs.resolve("server.log").toString();
            RollingFileAppender rollingFileAppender = new RollingFileAppender(layout, logFile, true);
            rollingFileAppender.setThreshold(Level.DEBUG);
            rollingFileAppender.setMaxFileSize("20MB");
            rollingFileAppender.setMaxBackupIndex(10);
            rootLogger.setLevel(Level.TRACE);
            rootLogger.addAppender(rollingFileAppender);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void parseParams() throws VersionException {
        // If by any reason 'version' is null we try to read it from the URI path, if not present an Exception is thrown
        if (version == null) {
            if (uriInfo.getPathParameters().containsKey("version")) {
                logger.warn("Setting 'version' from UriInfo object");
                this.version = uriInfo.getPathParameters().getFirst("version");
            } else {
                throw new VersionException("Version not valid: '" + version + "'");
            }
        }

        // Check version parameter, must be: v1, v2, ... If 'latest' then is converted to appropriate version.
        if (version.equalsIgnoreCase("latest")) {
            logger.info("Version 'latest' detected, setting 'version' parameter to 'v1'");
            version = "v1";
        }

        MultivaluedMap<String, String> multivaluedMap = uriInfo.getQueryParameters();
        queryOptions.put("metadata", multivaluedMap.get("metadata") == null || multivaluedMap.get("metadata").get(0).equals("true"));

        // By default, we will avoid counting the number of documents unless explicitly specified.
        queryOptions.put(QueryOptions.SKIP_COUNT, true);

        // Add all the others QueryParams from the URL
        for (Map.Entry<String, List<String>> entry : multivaluedMap.entrySet()) {
            String value = entry.getValue().get(0);
            switch (entry.getKey()) {
                case QueryOptions.INCLUDE:
                case QueryOptions.EXCLUDE:
                case QueryOptions.SORT:
                    queryOptions.put(entry.getKey(), new LinkedList<>(Splitter.on(",").splitToList(value)));
                    break;
                case QueryOptions.LIMIT:
                    limit = Integer.parseInt(value);
                    break;
                case QueryOptions.TIMEOUT:
                    queryOptions.put(entry.getKey(), Integer.parseInt(value));
                    break;
                case QueryOptions.SKIP:
                    int skip = Integer.parseInt(value);
                    queryOptions.put(entry.getKey(), (skip >= 0) ? skip : -1);
                    break;
                case QueryOptions.ORDER:
                    queryOptions.put(entry.getKey(), value);
                    break;
                case QueryOptions.SKIP_COUNT:
                    queryOptions.put(QueryOptions.SKIP_COUNT, Boolean.parseBoolean(value));
                    break;
                case Constants.INCREMENT_VERSION:
                    queryOptions.put(Constants.INCREMENT_VERSION, Boolean.parseBoolean(value));
                    break;
                case Constants.REFRESH:
                    queryOptions.put(Constants.REFRESH, Boolean.parseBoolean(value));
                    break;
                case "count":
                    count = Boolean.parseBoolean(value);
                    queryOptions.put(entry.getKey(), count);
                    break;
                case "includeIndividual": // SampleWS
                    lazy = !Boolean.parseBoolean(value);
                    queryOptions.put("lazy", lazy);
                    break;
                case "lazy":
                    lazy = Boolean.parseBoolean(value);
                    queryOptions.put(entry.getKey(), lazy);
                    break;
                case QueryOptions.FACET:
                case QueryOptions.FACET_RANGE:
                case QueryOptions.FACET_INTERSECTION:
                    queryOptions.put(entry.getKey(), value);
                    break;
                default:
                    // Query
                    query.put(entry.getKey(), value);
                    break;
            }
        }

        queryOptions.put(QueryOptions.LIMIT, (limit > 0) ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT);
        query.remove("sid");

//      Exceptions
        if (query.containsKey("status")) {
            query.put("status.name", query.get("status"));
            query.remove("status");
        }

        if (query.containsKey("variableSet")) {
            query.put("variableSetId", query.get("variableSet"));
            query.remove("variableSet");
        }
        if (query.containsKey("variableSetId")) {
            try {
                AbstractManager.MyResourceId resource = catalogManager.getStudyManager().getVariableSetId(query.getString
                        ("variableSetId"), query.getString("study"), sessionId);
                query.put("variableSetId", resource.getResourceId());
            } catch (CatalogException e) {
                logger.warn("VariableSetId parameter found, but proper id could not be found: {}", e.getMessage(), e);
            }
        }

        try {
            logger.info("URL: {}, query = {}, queryOptions = {}", uriInfo.getAbsolutePath().toString(),
                    jsonObjectWriter.writeValueAsString(query), jsonObjectWriter.writeValueAsString(queryOptions));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    private void parseIncludeExclude(MultivaluedMap<String, String> multivaluedMap, String key, String value) {
        if (value != null && !value.isEmpty()) {
            queryOptions.put(key, new LinkedList<>(Splitter.on(",").splitToList(value)));
        } else {
            queryOptions.put(key, (multivaluedMap.get(key) != null)
                    ? Splitter.on(",").splitToList(multivaluedMap.get(key).get(0))
                    : null);
        }
    }


    protected void addParamIfNotNull(Map<String, String> params, String key, Object value) {
        if (key != null && value != null) {
            params.put(key, value.toString());
        }
    }

    protected void addParamIfTrue(Map<String, String> params, String key, boolean value) {
        if (key != null && value) {
            params.put(key, Boolean.toString(value));
        }
    }

    // Temporal method used by deprecated methods. This will be removed at some point.
    protected AclParams getAclParams(
            @ApiParam(value = "Comma separated list of permissions to add", required = false) @QueryParam("add") String addPermissions,
            @ApiParam(value = "Comma separated list of permissions to remove", required = false) @QueryParam("remove") String removePermissions,
            @ApiParam(value = "Comma separated list of permissions to set", required = false) @QueryParam("set") String setPermissions)
            throws CatalogException {
        int count = 0;
        count += StringUtils.isNotEmpty(setPermissions) ? 1 : 0;
        count += StringUtils.isNotEmpty(addPermissions) ? 1 : 0;
        count += StringUtils.isNotEmpty(removePermissions) ? 1 : 0;
        if (count > 1) {
            throw new CatalogException("Only one of add, remove or set parameters are allowed.");
        } else if (count == 0) {
            throw new CatalogException("One of add, remove or set parameters is expected.");
        }

        String permissions = null;
        AclParams.Action action = null;
        if (StringUtils.isNotEmpty(addPermissions)) {
            permissions = addPermissions;
            action = AclParams.Action.ADD;
        }
        if (StringUtils.isNotEmpty(setPermissions)) {
            permissions = setPermissions;
            action = AclParams.Action.SET;
        }
        if (StringUtils.isNotEmpty(removePermissions)) {
            permissions = removePermissions;
            action = AclParams.Action.REMOVE;
        }
        return new AclParams(permissions, action);
    }

    @Deprecated
    @GET
    @Path("/help")
    @ApiOperation(value = "Help", hidden = true, position = 1)
    public Response help() {
        return createErrorResponse("help", "No help available");
    }

    protected Response createErrorResponse(Exception e) {
        // First we print the exception in Server logs
        logger.error("Catch error: " + e.getMessage(), e);

        // Now we prepare the response to client
        QueryResponse<ObjectMap> queryResponse = new QueryResponse<>();
        queryResponse.setTime(new Long(System.currentTimeMillis() - startTime).intValue());
        queryResponse.setApiVersion(version);
        queryResponse.setQueryOptions(queryOptions);
        if (StringUtils.isEmpty(e.getMessage())) {
            queryResponse.setError(e.toString());
        } else {
            queryResponse.setError(e.getMessage());
        }

        QueryResult<ObjectMap> result = new QueryResult<>();
        result.setWarningMsg("Future errors will ONLY be shown in the QueryResponse body");
        result.setErrorMsg("DEPRECATED: " + e.toString());
        queryResponse.setResponse(Arrays.asList(result));

        Response.Status errorStatus = Response.Status.INTERNAL_SERVER_ERROR;
        if (e instanceof CatalogAuthorizationException) {
            errorStatus = Response.Status.FORBIDDEN;
        } else if (e instanceof CatalogAuthenticationException) {
            errorStatus = Response.Status.UNAUTHORIZED;
        }

        return Response.fromResponse(createJsonResponse(queryResponse)).status(errorStatus).build();
    }

//    protected Response createErrorResponse(String o) {
//        QueryResult<ObjectMap> result = new QueryResult();
//        result.setErrorMsg(o.toString());
//        return createOkResponse(result);
//    }

    protected Response createErrorResponse(String method, String errorMessage) {
        try {
            return buildResponse(Response.ok(jsonObjectWriter.writeValueAsString(new ObjectMap("error", errorMessage)), MediaType.APPLICATION_JSON_TYPE));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return buildResponse(Response.ok("{\"error\":\"Error parsing json error\"}", MediaType.APPLICATION_JSON_TYPE));
    }

    // TODO: Change signature
    //    protected <T> Response createOkResponse(QueryResult<T> result)
    //    protected <T> Response createOkResponse(List<QueryResult<T>> results)
    protected Response createOkResponse(Object obj) {
        QueryResponse queryResponse = new QueryResponse();
        queryResponse.setTime(new Long(System.currentTimeMillis() - startTime).intValue());
        queryResponse.setApiVersion(version);
        queryResponse.setQueryOptions(queryOptions);

        // Guarantee that the QueryResponse object contains a list of results
        List list;
        if (obj instanceof List) {
            list = (List) obj;
        } else {
            list = new ArrayList();
            if (!(obj instanceof QueryResult)) {
                list.add(new QueryResult<>("", 0, 1, 1, "", "", Collections.singletonList(obj)));
            } else {
                list.add(obj);
            }
        }
        queryResponse.setResponse(list);

        return createJsonResponse(queryResponse);
    }

    //Response methods
    protected Response createOkResponse(Object o1, MediaType o2) {
        return buildResponse(Response.ok(o1, o2));
    }

    protected Response createOkResponse(Object o1, MediaType o2, String fileName) {
        return buildResponse(Response.ok(o1, o2).header("content-disposition", "attachment; filename =" + fileName));
    }


    protected Response createJsonResponse(QueryResponse queryResponse) {
        try {
            return buildResponse(Response.ok(jsonObjectWriter.writeValueAsString(queryResponse), MediaType.APPLICATION_JSON_TYPE));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            logger.error("Error parsing queryResponse object");
            return createErrorResponse("", "Error parsing QueryResponse object:\n" + Arrays.toString(e.getStackTrace()));
        }
    }

    protected Response buildResponse(Response.ResponseBuilder responseBuilder) {
        return responseBuilder
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Headers", "x-requested-with, content-type, authorization")
                .header("Access-Control-Allow-Credentials", "true")
                .header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                .build();
    }

    private void verifyHeaders(HttpHeaders httpHeaders) throws CatalogAuthenticationException {

        List<String> authorization = httpHeaders.getRequestHeader("Authorization");

        if (authorization != null && authorization.get(0).length() > 7) {
            String token = authorization.get(0);
            if (!token.startsWith("Bearer ")) {
                throw new CatalogAuthenticationException("Authorization header must start with Bearer JWToken");
            }
            this.sessionId = token.substring("Bearer".length()).trim();
        }

        if (StringUtils.isEmpty(this.sessionId)) {
            this.sessionId = this.params.getFirst("sid");
        }
    }
}
