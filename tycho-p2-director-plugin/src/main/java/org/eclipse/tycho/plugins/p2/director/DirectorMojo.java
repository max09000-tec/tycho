/*******************************************************************************
 * Copyright (c) 2023 Christoph Läubrich and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Christoph Läubrich - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.plugins.p2.director;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.internal.p2.director.app.DirectorApplication;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.eclipse.tycho.p2.CommandLineArguments;

/**
 * Allows to run the <a href=
 * "https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/guide/p2_director.html?cp=2_0_20_2">director
 * application</a> to manage Eclipse Installations. This mojo can be used in two ways
 * 
 * <ol>
 * <li>As a commandline invocation passing arguments as properties using
 * <code>mvn org.eclipse.tycho:tycho-p2-director-plugin:director -Ddestination=[target] ... -D... </code>
 * </li>
 * <li>as an execution inside a pom
 * 
 * <pre>
 * &lt;plugin&gt;
 *    &lt;groupId&gt;org.eclipse.tycho&lt;/groupId&gt;
 *    &lt;artifactId&gt;tycho-p2-director-plugin&lt;/artifactId&gt;
 *    &lt;version&gt;${tycho-version}&lt;/version&gt;
 *    &lt;executions&gt;
 *       &lt;execution&gt;
 *          &lt;goals&gt;
 *             &lt;goal&gt;director&lt;/goal&gt;
 *          &lt;/goals&gt;
 *          &lt;phase&gt;package&lt;/phase&gt;
 *          &lt;configuration&gt;
 *             &lt;destination&gt;...&lt;/destination&gt;
 *             ... other arguments ...
 *          &lt;/configuration&gt;
 *       &lt;/execution&gt;
 *    &lt;/executions&gt;
 * &lt;/plugin&gt;
 * </pre>
 * 
 * </li>
 * </ol>
 */
@Mojo(name = "director", defaultPhase = LifecyclePhase.NONE, threadSafe = true, requiresProject = false)
public class DirectorMojo extends AbstractMojo {

    @Component
    private IProvisioningAgent agent;

    /**
     * The folder in which the targeted product is located.
     */
    @Parameter(property = "destination", required = true)
    private File destination;

    /**
     * comma separated list of URLs denoting meta-data repositories
     */
    @Parameter(property = "metadatarepositories", alias = "metadatarepository")
    private String metadatarepositories;

    /**
     * comma separated list of URLs denoting artifact repositories.
     */
    @Parameter(property = "artifactrepositories", alias = "artifactrepository")
    private String artifactrepositories;

    /**
     * comma separated list denoting co-located meta-data and artifact repositories
     */
    @Parameter(property = "repositories", alias = "repository")
    private String repositories;

    /**
     * comma separated list of IUs to install, each entry in the list is in the form <id> [ '/'
     * <version> ].
     */
    @Parameter(property = "installIUs", alias = "installIU")
    private String installIUs;

    /**
     * Alternative way to specify the IU to install in a more declarative (but also verbose) way,
     * example:
     * 
     * <pre>
     * &lt;install&gt;
     *    &lt;iu&gt;
     *       &lt;id&gt;...&lt;/id&gt;
     *       &lt;version&gt;...optional version...&lt;/id&gt;
     *       &lt;feature&gt;true/false&lt;/feature&gt; &lt;!-- optional if true .feature.group is automatically added to the id  --&gt;
     * &lt;/install&gt;
     * </pre>
     */
    @Parameter
    private List<IU> install;

    /**
     * comma separated list of IUs to install, each entry in the list is in the form <id> [ '/'
     * <version> ].
     */
    @Parameter(property = "uninstallIUs", alias = "uninstallIU")
    private String uninstallIUs;

    /**
     * Alternative way to specify the IU to uninstall in a more declarative (but also verbose) way,
     * example:
     * 
     * <pre>
     * &lt;install&gt;
     *    &lt;iu&gt;
     *       &lt;id&gt;...&lt;/id&gt;
     *       &lt;version&gt;...optional version...&lt;/id&gt;
     *       &lt;feature&gt;true/false&lt;/feature&gt; &lt;!-- optional if true .feature.group is automatically added to the id  --&gt;
     * &lt;/install&gt;
     * </pre>
     */
    @Parameter
    private List<IU> uninstall;

    /**
     * comma separated list of numbers, revert the installation to a previous state. The number
     * representing the previous state of the profile as found in
     * p2/org.eclipse.equinox.p2.engine/<profileId>/.
     */
    @Parameter(property = "revert")
    private String revert;

    /**
     * Remove the history of the profile registry.
     */
    @Parameter(property = "purgeHistory")
    private boolean purgeHistory;

    /**
     * Lists all IUs found in the given repositories. IUs can optionally be listed. Each entry in
     * the list is in the form <id> [ '/' <version> ].
     */
    @Parameter(property = "list")
    private boolean list;

    /**
     * List the tags available
     */
    @Parameter(property = "listTags")
    private boolean listTags;

    /**
     * Lists all root IUs found in the given profile. Each entry in the list is in the form <id> [
     * '/' <version> ].
     */
    @Parameter(property = "listInstalledRoots")
    private boolean listInstalledRoots;

    /**
     * Formats the list of IUs according to the given string. Use ${property} for variable parts,
     * e.g. ${org.eclipse.equinox.p2.name} for the IU's name. ID and version of an IU are available
     * through ${id} and ${version}.
     */
    @Parameter(property = "listFormat")
    private String listFormat;

    /**
     * Defines what profile to use for the actions.
     */
    @Parameter(property = "profile")
    private String profile;

    /**
     * A list of properties in the form key=value pairs. Effective only when a new profile is
     * created. For example <tt>org.eclipse.update.install.features=true</tt> to install the
     * features into the product.
     */
    @Parameter(property = "profileproperties")
    private String profileproperties;

    @Parameter(property = "installFeatures")
    private boolean installFeatures;

    /**
     * Additional profile properties to set when materializing the product
     */
    @Parameter
    private Map<String, String> properties;

    /**
     * Path to a properties file containing a list of IU profile properties to set.
     */
    @Parameter(property = "iuProfileproperties")
    private File iuProfileproperties;

    /**
     * Defines what flavor to use for a newly created profile.
     */
    @Parameter(property = "flavor")
    private String flavor;

    /**
     * The location where the plug-ins and features will be stored. Effective only when a new
     * profile is created.
     */
    @Parameter(property = "bundlepool")
    private File bundlepool;

    /**
     * The OS to use when the profile is created.
     */
    @Parameter(property = "p2.os")
    private String p2os;

    /**
     * The windowing system to use when the profile is created.
     */
    @Parameter(property = "p2.ws")
    private String p2ws;

    /**
     * The architecture to use when the profile is created.
     */
    @Parameter(property = "p2.arch")
    private String p2arch;

    /**
     * The language to use when the profile is created.
     */
    @Parameter(property = "p2.nl")
    private String p2nl;

    /**
     * Indicates that the product resulting from the installation can be moved. Effective only when
     * a new profile is created.
     */
    @Parameter(property = "roaming")
    private boolean roaming;

    /**
     * Use a shared location for the install. The <path> defaults to ${user.home}/.p2
     */
    @Parameter(property = "shared")
    private String shared;

    /**
     * Tag the provisioning operation for easy referencing when reverting.
     */
    @Parameter(property = "tag")
    private String tag;

    /**
     * Only verify that the actions can be performed. Don't actually install or remove anything.
     */
    @Parameter(property = "verifyOnly")
    private boolean verifyOnly;

    /**
     * Only download the artifacts.
     */
    @Parameter(property = "downloadOnly")
    private boolean downloadOnly;

    /**
     * Follow repository references.
     */
    @Parameter(property = "followReferences")
    private boolean followReferences;

    /**
     * Whether to print detailed information about the content trust.
     */
    @Parameter(property = "verboseTrust")
    private boolean verboseTrust;

    /**
     * Whether to trust each artifact only if it is jar-signed or PGP-signed.
     */
    @Parameter(property = "trustSignedContentOnly")
    private boolean trustSignedContentOnly;

    /**
     * comma separated list of the authorities from which repository content, including repository
     * metadata, is trusted. An empty value will reject all remote connections.
     */
    @Parameter(property = "trustedAuthorities")
    private String trustedAuthorities;

    /**
     * comma separated list of the fingerprints of PGP keys to trust as signers of artifacts. An
     * empty value will reject all PGP keys.
     */
    @Parameter(property = "trustedPGPKeys")
    private String trustedPGPKeys;

    /**
     * The SHA-256 'fingerprints' of unanchored certficates to trust as signers of artifacts. An
     * empty value will reject all unanchored certificates.
     */
    @Parameter(property = "trustedCertificates")
    private String trustedCertificates;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        //TODO should be able to control agent creation see https://github.com/eclipse-equinox/p2/pull/398
        //Until now we need to fetch a service to trigger loading of the internal osgi framework...
        agent.getService(IArtifactRepositoryManager.class);
        CommandLineArguments args = new CommandLineArguments();
        args.addNonNull("-destination", destination);
        args.addNonNull("-metadatarepository", metadatarepositories);
        args.addNonNull("-artifactrepository", artifactrepositories);
        args.addNonNull("-repository", repositories);
        args.addNotEmpty("-installIU", getUnitParameterList(installIUs, install), ",");
        args.addNotEmpty("-uninstallIU", getUnitParameterList(uninstallIUs, uninstall), ",");
        args.addNonNull("-revert", revert);
        args.addFlagIfTrue("-purgeHistory", purgeHistory);
        args.addFlagIfTrue("-list", list);
        args.addFlagIfTrue("-listTags", listTags);
        args.addFlagIfTrue("-listInstalledRoots", listInstalledRoots);
        args.addNonNull("-listFormat", listFormat);
        args.addNonNull("-profile", profile);
        args.addNotEmpty("-profileproperties", getPropertyMap(profileproperties, properties), "=", ",");
        args.addNonNull("-iuProfileproperties", iuProfileproperties);
        args.addNonNull("-flavor", flavor);
        args.addNonNull("-bundlepool", bundlepool);
        args.addNonNull("-p2.os", p2os);
        args.addNonNull("-p2.ws", p2ws);
        args.addNonNull("-p2.arch", p2arch);
        args.addNonNull("-p2.nl", p2nl);
        args.addFlagIfTrue("-roaming", roaming);
        args.addNonNull("-trustedAuthorities", trustedAuthorities);
        if (shared != null) {
            if (shared.isEmpty()) {
                args.add("-shared");
            } else {
                args.addNonNull("-shared", new File(shared));
            }
        }
        args.addNonNull("-tag", tag);
        args.addFlagIfTrue("-verifyOnly", verifyOnly);
        args.addFlagIfTrue("-downloadOnly", downloadOnly);
        args.addFlagIfTrue("-followReferences", followReferences);
        args.addFlagIfTrue("-verboseTrust", verboseTrust);
        args.addFlagIfTrue("-trustSignedContentOnly", trustSignedContentOnly);
        args.addNonNull("-trustedAuthorities", trustedAuthorities);
        args.addNonNull("-trustedPGPKeys", trustedPGPKeys);
        args.addNonNull("-trustedCertificates", trustedCertificates);
        Object exitCode = new DirectorApplication().run(args.toArray());
        if (!(IApplication.EXIT_OK.equals(exitCode))) {
            throw new MojoFailureException("Call to p2 director application failed with exit code " + exitCode
                    + ". Program arguments were: '" + args + "'.");
        }
    }

    private Map<String, String> getPropertyMap(String csvPropertiesMap, Map<String, String> properties) {
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
        if (csvPropertiesMap != null) {
            for (String keyValue : csvPropertiesMap.split(",")) {
                String[] split = keyValue.split("=");
                map.put(split[0].trim(), split[1].trim());
            }
        }
        if (properties != null) {
            map.putAll(properties);
        }
        if (installFeatures) {
            map.put("org.eclipse.update.install.features", "true");
        }
        return map;
    }

    private List<String> getUnitParameterList(String csvlist, List<IU> units) {
        List<String> list = new ArrayList<String>();
        if (csvlist != null) {
            for (String iu : csvlist.split(",")) {
                list.add(iu.trim());
            }
        }
        if (install != null) {
            for (IU iu : install) {
                String id = iu.id;
                if (iu.feature) {
                    id += ".feature.group";
                }
                if (iu.version != null) {
                    id += "/" + iu.version;
                }
                list.add(id);
            }
        }
        return list;
    }

    private void add(String key, String value, List<String> args) {
        if (metadatarepositories != null) {
            args.add("-metadatarepository");
            args.add(metadatarepositories);
        }
    }

    public static final class IU {
        String id;
        String version;
        boolean feature;
    }

}