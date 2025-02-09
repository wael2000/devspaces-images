import groovy.json.JsonSlurper

def curlCMD = "https://raw.github.com/redhat-developer/codeready-workspaces/crw-2-rhel-8/dependencies/job-config.json".toURL().text

def jsonSlurper = new JsonSlurper();
def config = jsonSlurper.parseText(curlCMD);

// map branch to floating quay tag to create
def JOB_BRANCHES = config."Management-Jobs"."push-latest-container-to-quay"?.keySet()
for (JB in JOB_BRANCHES) {
    //check for jenkinsfile
    FILE_CHECK = false
    try {
        fileCheck = readFileFromWorkspace('jobs/CRW_CI/push-latest-container-to-quay_'+JB+'.jenkinsfile')
        FILE_CHECK = true
    }
    catch(err) {
        println "No jenkins file found for " + JB
    }
    if (FILE_CHECK) {
        JOB_BRANCH=""+JB
        MIDSTM_BRANCH="crw-" + JOB_BRANCH.replaceAll(".x","") + "-rhel-8"
        FLOATING_QUAY_TAGS="" + config.Other."FLOATING_QUAY_TAGS"[JB]
        jobPath="${FOLDER_PATH}/${ITEM_NAME}_" + JOB_BRANCH
        pipelineJob(jobPath){
            disabled(config."Management-Jobs"."push-latest-container-to-quay"[JB].disabled) // on reload of job, disable to avoid churn
            description('''
Push 1 or more containers from OSBS to quay.io/crw/. 
Triggered by  <a href=../get-sources-rhpkg-container-build_''' + JOB_BRANCH + '''/>get-sources-rhpkg-container-build</a>, but can be used manually too.
   
<p>
  
Images to copy to quay (18):
<table>
<tr><td>

  <li> <a href=https://quay.io/repository/crw/backup-rhel8?tab=tags>backup</a> @since 2.12</li>
  <li> <a href=https://quay.io/repository/crw/configbump-rhel8?tab=tags>configbump</a> </li>
  <li> <a href=https://quay.io/repository/crw/crw-2-rhel8-operator?tab=tags>operator</a> 
  <li> <a href=https://quay.io/repository/crw/crw-2-rhel8-operator-bundle?tab=tags>operator-bundle</a> @since 2.12
  <li> <a href=https://quay.io/repository/crw/dashboard-rhel8?tab=tags>dashboard</a> @since 2.9</li>

  </td><td>

  <li> <a href=https://quay.io/repository/crw/devfileregistry-rhel8?tab=tags>devfileregistry</a></li>
  <li> <a href=https://quay.io/repository/crw/idea-rhel8?tab=tags>idea</a> @since 2.11</li>
  <li> <a href=https://quay.io/repository/crw/imagepuller-rhel8?tab=tags>imagepuller</a></li>
  <li> <a href=https://quay.io/repository/crw/jwtproxy-rhel8?tab=tags>jwtproxy</a> </li>
  <li> <a href=https://quay.io/repository/crw/machineexec-rhel8?tab=tags>machineexec</a> </li>

  </td><td>

  <li> <a href=https://quay.io/repository/crw/pluginbroker-artifacts-rhel8?tab=tags>pluginbroker-artifacts</a> </li>
  <li> <a href=https://quay.io/repository/crw/pluginbroker-metadata-rhel8?tab=tags>pluginbroker-metadata</a></li>
  <li> <a href=https://quay.io/repository/crw/pluginregistry-rhel8?tab=tags>pluginregistry</a></li>
  <li> <a href=https://quay.io/repository/crw/server-rhel8?tab=tags>server</a> </li>
  <li> <a href=https://quay.io/repository/crw/theia-rhel8?tab=tags>theia</a> </li>

  </td><td>

  <li> <a href=https://quay.io/repository/crw/theia-dev-rhel8?tab=tags>theia-dev</a> </li>
  <li> <a href=https://quay.io/repository/crw/theia-endpoint-rhel8?tab=tags>theia-endpoint</a> </li>
  <li> <a href=https://quay.io/repository/crw/traefik-rhel8?tab=tags>traefik</a> </li>
  <li> <a href=https://quay.io/repository/crw/udi-rhel8?tab=tags>udi</a></li>
  </td></tr>
  </table>
</ul>
            <p>NOTE:  If no nodes are available, run: <br/>
    <b><a href=https://github.com/redhat-developer/codeready-workspaces/blob/crw-2-rhel-8/product/getLatestImageTags.sh>getLatestImageTags.sh</a> 
    -c "codeready-workspaces-udi-rhel8 codeready-workspaces-dashboard-rhel8" --osbs --pushtoquay="''' + 
    (JOB_BRANCH.equals("2.x") ? '''2.y next''' : JOB_BRANCH+''' latest''') + 
    '''"</b>
  
  to get latest from osbs and push to quay.

  <p>After this job runs, <a href=../update-digests-in-metadata_''' + JOB_BRANCH + '''>update-digests-in-metadata</a> will be triggered to check if those containers need a respin.
            ''')

            properties {
                ownership {
                    primaryOwnerId("nboldt")
                }

                disableResumeJobProperty()
            }

            throttleConcurrentBuilds {
                maxPerNode(2)
                maxTotal(10)
            }

            quietPeriod(120) // limit builds to 1 every 2 mins (in sec)

            logRotator {
                daysToKeep(45)
                numToKeep(90)
                artifactDaysToKeep(2)
                artifactNumToKeep(1)
            }

            /* requires naginator plugin */
            /* publishers {
                retryBuild {
                    rerunIfUnstable()
                    retryLimit(1)
                    progressiveDelay(30,90)
                }
            } */

            parameters{ 
                if (JB.equals("2.14") || JB.equals("2.15")) {
                    textParam("CONTAINERS", '''\
backup configbump operator operator-bundle operator-metadata \
dashboard devfileregistry idea imagepuller jwtproxy \
machineexec pluginbroker-metadata pluginbroker-artifacts plugin-java8 \
plugin-java11 plugin-openshift plugin-kubernetes pluginregistry \
server stacks-cpp stacks-dotnet stacks-golang stacks-php \
theia theia-dev theia-endpoint traefik''', '''list of 29 containers to copy:<br/>
* no 'crw/' or 'codeready-workspaces-' prefix><br/>
* no '-rhel8' suffix<br/>
* include one, some, or all as needed''')
                } else { 
                    // 2.16: TODO remove stacks and reduce image count to 19
                    textParam("CONTAINERS", '''\
configbump operator operator-bundle dashboard devfileregistry \
idea imagepuller jwtproxy machineexec pluginbroker-metadata \
pluginbroker-artifacts pluginregistry server theia \
theia-dev theia-endpoint traefik udi''', '''list of 18 containers to copy:<br/>
* no 'crw/' or 'codeready-workspaces-' prefix><br/>
* no '-rhel8' suffix<br/>
* include one, some, or all as needed''')
                }
                stringParam("MIDSTM_BRANCH", MIDSTM_BRANCH, "")
                stringParam("FLOATING_QUAY_TAGS", FLOATING_QUAY_TAGS, "Update :" + FLOATING_QUAY_TAGS + " tag in addition to latest (2.y-zz) and base (2.y) tags.")
                booleanParam("CLEAN_ON_FAILURE", true, "If false, don't clean up workspace after the build so it can be used for debugging.")
            }

            // TODO: enable naginator plugin to re-trigger if job fails

            // TODO: add email notification to nboldt@, anyone who submits a bad build, etc.

            // TODO: enable console log parser ?

            definition {
                cps{
                    sandbox(true)
                    script(readFileFromWorkspace('jobs/CRW_CI/push-latest-container-to-quay_'+JOB_BRANCH+'.jenkinsfile'))
                }
            }
        }
    }
}