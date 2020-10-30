package eu.xenit.alfresco.issue.platformsample;

import java.util.HashMap;
import org.alfresco.model.ContentModel;
import org.alfresco.rad.test.AbstractAlfrescoIT;
import org.alfresco.rad.test.AlfrescoTestRunner;
import org.alfresco.repo.nodelocator.CompanyHomeNodeLocator;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.NodeRef;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(value = AlfrescoTestRunner.class)
public class AclConcurrencyProblemReproductions extends AbstractAlfrescoIT {

    private NodeRef rootFolder;

    @Before
    public void setup() {
        rootFolder = getServiceRegistry().getRetryingTransactionHelper()
                .doInTransaction(this::createOrResetTestFolder, false, true);
    }

    /**
     * Setup: sub-folder -> sub-sub-folder -> nodes
     */
    @Test
    public void exception_setFixedAcls_unexpectedSharedAcl() throws InterruptedException {
        // 1. Create a nested folder structure
        NodeRef subFolder = getServiceRegistry().getRetryingTransactionHelper().doInTransaction(() ->
                getServiceRegistry().getFileFolderService()
                        .create(rootFolder, "sub-folder", ContentModel.TYPE_FOLDER).getNodeRef(), false, true);
        NodeRef subSubFolder = getServiceRegistry().getRetryingTransactionHelper().doInTransaction(() ->
                getServiceRegistry().getFileFolderService()
                        .create(subFolder, "sub-sub-folder", ContentModel.TYPE_FOLDER).getNodeRef(), false, true);

        // 2. Modify permissions the "sub-folder" - and create new nodes in the "sub-sub-folder" using different threads

        // 2.1 Create thread in which we will update the permissions of the sub folder
        Runnable setPermissionAction = () -> {
            AuthenticationUtil.setAdminUserAsFullyAuthenticatedUser();
            getServiceRegistry().getRetryingTransactionHelper().doInTransaction(() -> {
                getServiceRegistry().getPermissionService().setInheritParentPermissions(subFolder, false);
                return null;
            }, false, true);
        };
        Thread setPermissionsThread = new Thread(setPermissionAction);

        // 2.2 Create thread in which we will create new nodes in the child folder
        final int numberOfNodesToCreate = 50;
        Runnable createNodesRunnable = () -> {
            AuthenticationUtil.setAdminUserAsFullyAuthenticatedUser();
            getServiceRegistry().getRetryingTransactionHelper().doInTransaction(() -> {
                for (int i = 1; i <= numberOfNodesToCreate; i++) {
                    final String name = "-" + i + "-thread-" + Thread.currentThread().getName();
                    getServiceRegistry().getFileFolderService().create(subSubFolder, name, ContentModel.PROP_CONTENT);
                }
                return null;
            }, false, true);
        };
        Thread createNodesThread = new Thread(createNodesRunnable);

        createNodesThread.start();
        setPermissionsThread.start();

        setPermissionsThread.join();
        createNodesThread.join();

        /*
        At this point the harm has already been done - the created child nodes can have a shared ACL that differs from
        the ACL of the sub folder.
        To illustrate this incorrect state, we now try to set the permissions of the sub-sub-folder. This wil result in
        the "setFixedAcls: unexpected shared ACL" exception
         */

        getServiceRegistry().getPermissionService().setInheritParentPermissions(subSubFolder, false);
    }

    private NodeRef createOrResetTestFolder() {
        final String name = this.getClass().getSimpleName();
        final NodeRef existing = getServiceRegistry().getNodeService()
                .getChildByName(getCompanyHomeNodeRef(), ContentModel.ASSOC_CONTAINS, name);

        if (existing != null) {
            getServiceRegistry().getNodeService()
                    .addAspect(existing, ContentModel.ASPECT_TEMPORARY, new HashMap<>());
            getServiceRegistry().getNodeService().deleteNode(existing);
        }

        return getServiceRegistry().getFileFolderService()
                .create(getCompanyHomeNodeRef(), name, ContentModel.TYPE_FOLDER)
                .getNodeRef();
    }

    private NodeRef getCompanyHomeNodeRef() {
        return getServiceRegistry().getNodeLocatorService().getNode(CompanyHomeNodeLocator.NAME, null, null);
    }

}
