package com.todoroo.astrid.gtasks;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.view.MenuInflater;

import com.timsu.astrid.R;
import com.todoroo.andlib.data.Property;
import com.todoroo.andlib.data.TodorooCursor;
import com.todoroo.andlib.service.Autowired;
import com.todoroo.andlib.utility.DateUtilities;
import com.todoroo.andlib.utility.DialogUtilities;
import com.todoroo.astrid.dao.StoreObjectDao;
import com.todoroo.astrid.data.StoreObject;
import com.todoroo.astrid.data.Task;
import com.todoroo.astrid.gtasks.sync.GtasksSyncService;
import com.todoroo.astrid.service.SyncV2Service;
import com.todoroo.astrid.subtasks.OrderedListFragmentHelper;
import com.todoroo.astrid.subtasks.SubtasksListFragment;

public class GtasksListFragment extends SubtasksListFragment {

    protected static final int MENU_CLEAR_COMPLETED_ID = MENU_ADDON_INTENT_ID + 1;

    public static final String TOKEN_STORE_ID = "storeId"; //$NON-NLS-1$

    protected static final int MENU_REFRESH_ID = MENU_SUPPORT_ID + 1;

    @Autowired private StoreObjectDao storeObjectDao;

    @Autowired private GtasksTaskListUpdater gtasksTaskListUpdater;

    @Autowired private GtasksMetadataService gtasksMetadataService;

    @Autowired private GtasksSyncService gtasksSyncService;

    @Autowired private GtasksPreferenceService gtasksPreferenceService;

    @Autowired private SyncV2Service syncService;

    private StoreObject list;

    private static final Property<?>[] LIST_PROPERTIES = new Property<?>[] {
        StoreObject.ID,
        StoreObject.TYPE,
        GtasksList.REMOTE_ID,
        GtasksList.ORDER,
        GtasksList.NAME,
        GtasksList.LAST_SYNC
    };

    @Override
    protected OrderedListFragmentHelper<?> createFragmentHelper() {
        return new OrderedListFragmentHelper<StoreObject>(this, gtasksTaskListUpdater) {
            @Override
            protected void onMetadataChanged(long targetTaskId) {
                gtasksSyncService.triggerMoveForMetadata(gtasksMetadataService.
                        getTaskMetadata(targetTaskId));
            }
        };
    }

    @Override
    protected boolean allowResorting() {
        return false;
    }

    @Override
    public void onActivityCreated(Bundle icicle) {
        super.onActivityCreated(icicle);

        long storeObjectId = getActivity().getIntent().getLongExtra(TOKEN_STORE_ID, 0);
        list = storeObjectDao.fetch(storeObjectId, LIST_PROPERTIES);
        ((OrderedListFragmentHelper<StoreObject>)helper).setList(list);
    }

    @Override
    protected void initiateAutomaticSync() {
        if (list != null && DateUtilities.now() - list.getValue(GtasksList.LAST_SYNC) > DateUtilities.ONE_HOUR) {
            syncService.synchronizeList(list, false, syncActionHelper.syncResultCallback);
        }
    }

    @Override
    protected void onTaskDelete(Task task) {
        helper.onDeleteTask(task);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        MenuItem item = menu.add(Menu.NONE, MENU_CLEAR_COMPLETED_ID, Menu.FIRST,
                this.getString(R.string.gtasks_GTA_clear_completed));
        item.setIcon(android.R.drawable.ic_input_delete); // Needs new icon
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // handle my own menus
        switch (item.getItemId()) {
        case MENU_REFRESH_ID:
            syncService.synchronizeList(list, true, syncActionHelper.syncResultCallback);
            return true;
        case MENU_CLEAR_COMPLETED_ID:
            clearCompletedTasks();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void clearCompletedTasks() {
        final ProgressDialog pd = new ProgressDialog(getActivity());
        final TodorooCursor<Task> tasks = taskService.fetchFiltered(filter.sqlQuery,
                null, Task.ID, Task.COMPLETION_DATE);
        pd.setMessage(this.getString(R.string.gtasks_GTA_clearing));
        pd.show();

        new Thread() {
            @Override
            public void run() {
                String listId = null;
                try {
                    for (tasks.moveToFirst(); !tasks.isAfterLast(); tasks.moveToNext()) {
                        Task t = new Task(tasks);
                        if (t.isCompleted()) {
                            if (listId == null) {
                                listId = gtasksMetadataService.getTaskMetadata(
                                        t.getId()).getValue(GtasksMetadata.LIST_ID);
                            }
                            t.setValue(Task.DELETION_DATE, DateUtilities.now());
                            taskService.save(t);
                        }
                    }
                } finally {
                    tasks.close();
                    DialogUtilities.dismissDialog(getActivity(), pd);
                }
                if (listId != null) {
                    gtasksTaskListUpdater.correctMetadataForList(listId);
                }
                Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(new Runnable() {
                        public void run() {
                            loadTaskListContent(true);
                        }
                    });
                }
            }
        }.start();
    }

    @Override
    protected void addSyncRefreshMenuItem(Menu menu) {
        if(gtasksPreferenceService.isLoggedIn()) {
            MenuItem item = menu.add(Menu.NONE, MENU_REFRESH_ID, Menu.NONE,
                    R.string.actfm_TVA_menu_refresh);
            item.setIcon(R.drawable.ic_menu_refresh);
        } else {
            super.addSyncRefreshMenuItem(menu);
        }
    }

}
