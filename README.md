# Changing permission inheritance CORRUPTS Alfresco ACL

Summary of the problem: if you disable permission inheritance on a folder, while someone else adds documents - 
Alfresco ACLs get corrupted.

## Steps to reproduce

Assume we have a folder (`folder`) which inherits permissions: it that has a `SHARED` ACL with example ID "11". 

Now simultaneously perform following actions:
1. disable permission inheritance for `folder`
2. insert new nodes in `folder`

The simultaneous executing of these actions can for example happen in separate http threads. 

The result is that the folder will have a new `DEFINED` ACL, with ID for example "200". 

**TO-BE:**

All child nodes of `folder` to have a `SHARED` ACL that inherits from ACL 200. 

**AS-IS:**

Child nodes of `folder` have the old ACL with 11.

This is a corrupted ACL state since the child now inherits a different ACL than the one it should inherit from the 
parent. 

An Alfresco integration test to reproduce this illegal state is include in the `AclConcurrencyProblemReproductions`
class. 

## Project setup
 
This project uses the Alfresco SDK version 4.1. For usage instructions see: 
https://github.com/Alfresco/alfresco-sdk/tree/master/archetypes/alfresco-platform-jar-archetype/src/main/resources/archetype-resources

Run tasks that can be used to execute the tests reproducing the illegal ACL state:

 * `build_start_it_supported`. Build the whole project including dependencies required for IT execution, recreate the ACS docker image, start the dockerised environment 
 composed by ACS, Share (optional), ASS and PostgreSQL and tail the logs of all the containers.
 * `test`. Execute the integration tests (the environment must be already started).
