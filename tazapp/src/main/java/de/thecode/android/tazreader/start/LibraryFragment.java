package de.thecode.android.tazreader.start;


import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.RecyclerView;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import de.greenrobot.event.EventBus;
import de.thecode.android.tazreader.R;
import de.thecode.android.tazreader.data.Paper;
import de.thecode.android.tazreader.data.TazSettings;
import de.thecode.android.tazreader.download.CoverDownloadedEvent;
import de.thecode.android.tazreader.sync.SyncHelper;
import de.thecode.android.tazreader.sync.SyncStateChangedEvent;
import de.thecode.android.tazreader.utils.BaseFragment;
import de.thecode.android.tazreader.widget.AutofitRecyclerView;

import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt;

import java.lang.ref.WeakReference;

import timber.log.Timber;

/**
 * A simple {@link Fragment} subclass.
 */
public class LibraryFragment extends BaseFragment
        implements LoaderManager.LoaderCallbacks<Cursor>, LibraryAdapter.OnItemClickListener,
        LibraryAdapter.OnItemLongClickListener {
    WeakReference<IStartCallback> callback;
    LibraryAdapter                adapter;
    SwipeRefreshLayout            swipeRefresh;

    ActionMode actionMode;

    boolean isSyncing;

    private AutofitRecyclerView  recyclerView;
    private FloatingActionButton fabArchive;

    private TazSettings.OnPreferenceChangeListener demoModeChangedListener = new TazSettings.OnPreferenceChangeListener() {
        @Override
        public void onPreferenceChanged(String key, SharedPreferences preferences) {
            onDemoModeChanged(preferences.getBoolean(key, true));
        }
    };

    public LibraryFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {


        setHasOptionsMenu(true);

        callback = new WeakReference<>((IStartCallback) getActivity());

        View view = inflater.inflate(R.layout.start_library, container, false);

        swipeRefresh = (SwipeRefreshLayout) view.findViewById(R.id.swipeRefreshLayout);
        swipeRefresh.setColorSchemeResources(R.color.refresh_color_1, R.color.refresh_color_2);
        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {

                hideFab();
                SyncHelper.requestSync(getContext());
            }
        });


        recyclerView = (AutofitRecyclerView) view.findViewById(R.id.recycler);
        recyclerView.setHasFixedSize(true);

        adapter = new LibraryAdapter(getActivity(), null, getCallback());
        adapter.setHasStableIds(true);

        fabArchive = (FloatingActionButton) view.findViewById(R.id.fabArchive);
        fabArchive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (hasCallback()) getCallback().callArchive();
            }
        });

        showFab();

        adapter.setOnItemClickListener(this);
        adapter.setOnItemLongClickListener(this);
        recyclerView.setAdapter(adapter);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    AutofitRecyclerView.AutoFitGridLayoutManager lm = ((AutofitRecyclerView) recyclerView).getLayoutManager();
                    int lastCompletlyVisible = lm.findLastCompletelyVisibleItemPosition();
                    int itemCount = lm.getItemCount();
                    if (lastCompletlyVisible == itemCount - 1) {
                        showFab();
                    }
                }

            }
        });

        recyclerView.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(View view) {
                startTutorial();
            }

            @Override
            public void onChildViewDetachedFromWindow(View view) {
            }
        });


        if (hasCallback()) getCallback().onUpdateDrawer(this);
        getLoaderManager().initLoader(0, null, this);


        //ActionMode enabling after view because of theme bugs
        view.post(new Runnable() {
            @Override
            public void run() {
                if (hasCallback()) {

                    if (getCallback().getRetainData()
                                     .isActionMode()) setActionMode();
                }
            }
        });

        setHasOptionsMenu(true);

        return view;
    }

    private boolean hasCallback() {
        return callback.get() != null;
    }

    private IStartCallback getCallback() {
        return callback.get();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.start_library, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.ic_action_edit:
                setActionMode();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Timber.d("%s", TazSettings.getInstance(getActivity())
                                  .getPrefBoolean(TazSettings.PREFKEY.FORCESYNC, false));
        if (TazSettings.getInstance(getActivity())
                       .getPrefBoolean(TazSettings.PREFKEY.FORCESYNC, false)) {

            SyncHelper.requestSync(getContext());
        }
    }

    @Override
    public void onPause() {
        if (hasCallback()) getCallback().getRetainData()
                                        .removeOpenPaperIdAfterDownload();
        super.onPause();
    }

    @Override
    public void onStart() {
        super.onStart();
        TazSettings.getInstance(getContext())
                   .addOnPreferenceChangeListener(TazSettings.PREFKEY.DEMOMODE, demoModeChangedListener);
        EventBus.getDefault()
                .registerSticky(this);
    }

    @Override
    public void onStop() {
        TazSettings.getInstance(getContext())
                   .removeOnPreferenceChangeListener(demoModeChangedListener);
        EventBus.getDefault()
                .unregister(this);
        super.onStop();
    }

    private void onDemoModeChanged(boolean demoMode) {
        showFab();
        getLoaderManager().restartLoader(0, null, this);
    }

    @Override
    public void onDestroyView() {

        int firstVisible = recyclerView.findFirstVisibleItemPosition();
        int lastVisible = recyclerView.findLastVisibleItemPosition();
        for (int i = firstVisible; i <= lastVisible; i++) {
            LibraryAdapter.ViewHolder vh = (LibraryAdapter.ViewHolder) recyclerView.findViewHolderForLayoutPosition(i);
            if (vh != null) {
                EventBus.getDefault()
                        .unregister(vh);
            }
        }
        adapter.destroy();
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        adapter.removeClickLIstener();
        super.onDestroy();
    }

//    @Override
//    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
//        super.onActivityCreated(savedInstanceState);
//        new MaterialTapTargetPrompt.Builder(getActivity())
//                .setTarget(getActivity().findViewById(R.id.fab))
//                .setPrimaryText("Send your first email")
//                .setSecondaryText("Tap the envelop to start composing your first email")
//                .setOnHidePromptListener(new MaterialTapTargetPrompt.OnHidePromptListener()
//                {
//                    @Override
//                    public void onHidePrompt(MotionEvent event, boolean tappedTarget)
//                    {
//                        //Do something such as storing a value so that this prompt is never shown again
//                    }
//
//                    @Override
//                    public void onHidePromptComplete()
//                    {
//
//                    }
//                })
//                .show();
//    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {


        StringBuilder selection = new StringBuilder();
        boolean demo = true;
        if (hasCallback()) {
            demo = TazSettings.getInstance(getContext())
                              .isDemoMode();
        }

        if (demo) selection.append("(");
        selection.append(Paper.Columns.FULL_VALIDUNTIL)
                 .append(" > ")
                 .append(System.currentTimeMillis() / 1000);

        if (demo) {
            selection.append(" AND ");
            selection.append(Paper.Columns.ISDEMO)
                     .append("=1");
            selection.append(")");
        }
        selection.append(" OR ")
                 .append(Paper.Columns.ISDOWNLOADED)
                 .append("=1");
        selection.append(" OR ")
                 .append(Paper.Columns.DOWNLOADID)
                 .append("!=0");
        selection.append(" OR ")
                 .append(Paper.Columns.IMPORTED)
                 .append("=1");
        selection.append(" OR ")
                 .append(Paper.Columns.HASUPDATE)
                 .append("=1");
        selection.append(" OR ")
                 .append(Paper.Columns.KIOSK)
                 .append("=1");

        return new CursorLoader(getActivity(), Paper.CONTENT_URI, null, selection.toString(), null, Paper.Columns.DATE + " DESC");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        Timber.i("loader: %s, data: %s", loader, data);
        adapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        Timber.d("loader: %s", loader);
    }


    public void onEventMainThread(SyncStateChangedEvent event) {
        isSyncing = event.isRunning();
        Timber.d("SyncStateChanged running: %s", isSyncing);
        if (swipeRefresh.isRefreshing() != event.isRunning()) swipeRefresh.setRefreshing(event.isRunning());
        if (isSyncing) hideFab();
        else showFab();


    }

    public void onEventMainThread(CoverDownloadedEvent event) {
        try {
            LibraryAdapter.ViewHolder viewHolder = (LibraryAdapter.ViewHolder) recyclerView.findViewHolderForItemId(
                    event.getPaperId());
            if (viewHolder != null) viewHolder.image.setTag(null);
            adapter.notifyItemChanged(adapter.getItemPosition(event.getPaperId()));
        } catch (IllegalStateException e) {
            Timber.w(e);
        }
    }

    public void onEventMainThread(ScrollToPaperEvent event) {
        Timber.d("event: %s", event);
        if (recyclerView != null && adapter != null) {
            recyclerView.smoothScrollToPosition(adapter.getItemPosition(event.getPaperId()));
        }
    }

    public void onEventMainThread(DrawerStateChangedEvent event) {
        Timber.d("event: %s", event.getNewState());
        if (event.getNewState() == DrawerLayout.STATE_IDLE) swipeRefresh.setEnabled(true);
        else swipeRefresh.setEnabled(false);
    }


    @Override
    public void onItemClick(View v, int position, Paper paper) {
        Timber.d("v: %s, position: %s, paper: %s", v, position, paper);
        if (actionMode != null) onItemLongClick(v, position, paper);
        else {
            switch (paper.getState()) {
                case Paper.DOWNLOADED_READABLE:
                case Paper.DOWNLOADED_BUT_UPDATE:
                    //openPlayer(paper.getId());
                    if (hasCallback()) getCallback().openReader(paper.getId());
                    break;
                case Paper.IS_DOWNLOADING:

                    break;

                case Paper.NOT_DOWNLOADED:
                case Paper.NOT_DOWNLOADED_IMPORT:
                    try {
                        if (hasCallback()) getCallback().startDownload(paper.getId());
                    } catch (Paper.PaperNotFoundException e) {
                        Timber.e(e);
                    }
                    break;

            }

        }
    }


    @Override
    public boolean onItemLongClick(View v, int position, Paper paper) {
        setActionMode();
        Timber.d("v: %s, position: %s, paper: %s", v, position, paper);
        ;
        if (!adapter.isSelected(paper.getId())) selectPaper(paper.getId());
        else deselectPaper(paper.getId());
        return true;
    }


    private void deleteSelected() {
        if (adapter.getSelected() != null && adapter.getSelected()
                                                    .size() > 0) {
            Long[] ids = adapter.getSelected()
                                .toArray(new Long[adapter.getSelected()
                                                         .size()]);
            if (hasCallback()) getCallback().getRetainData()
                                            .deletePaper(ids);
        }
    }

    private void downloadSelected() {
        for (Long paperId : adapter.getSelected()) {
            try {
                Paper paper = new Paper(getActivity(), paperId);
                if (hasCallback()) getCallback().startDownload(paper.getId());
            } catch (Paper.PaperNotFoundException e) {
                Timber.e(e);
            }
        }
        adapter.deselectAll();
    }


    private void showFab() {
        if (!TazSettings.getInstance(getContext())
                        .isDemoMode()) {
            if (!isSyncing) {
                fabArchive.show();
            }
        } else {
            hideFab();
        }
    }

    private void hideFab() {
        fabArchive.hide();
    }


    public void setActionMode() {
        if (actionMode == null) getActivity().startActionMode(new ActionModeCallback());
    }

    public void selectPaper(long paperId) {
        if (adapter != null) adapter.select(paperId);
        if (actionMode != null) actionMode.invalidate();
    }

    public void deselectPaper(long paperId) {
        if (adapter != null) adapter.deselect(paperId);
        if (actionMode != null) actionMode.invalidate();
    }

    public void startTutorial() {
        if (!TazSettings.getInstance(getActivity())
                        .isTutorialStepFinished("PAPER") && TazSettings.getInstance(getActivity())
                                                                       .isTutorialStepFinished("MAINMENU")) {
            if (recyclerView.getLayoutManager()
                            .getChildCount() > 2) {
                View view1 = recyclerView.getLayoutManager()
                                        .findViewByPosition(0);
                View view2 = recyclerView.getLayoutManager()
                                         .findViewByPosition(2);
                if (view1 != null && view2!= null) {
                    LibraryAdapter.ViewHolder viewHolder1 = (LibraryAdapter.ViewHolder) recyclerView.getChildViewHolder(view1);
                    LibraryAdapter.ViewHolder viewHolder2 = (LibraryAdapter.ViewHolder) recyclerView.getChildViewHolder(view2);
                    new MaterialTapTargetPrompt.Builder(getActivity(), R.style.MaterialTapTargetPromptTheme).setTarget(
                            viewHolder1.card)

                                                                                                            .setPrimaryText(
                                                                                                                    "Die Ausgaben")
                                                                                                            .setSecondaryText(
                                                                                                                    "Klicken zum herunterladen\nLange klicken für weiter Aktionen")
                                                                                                            .setOnHidePromptListener(
                                                                                                                    new MaterialTapTargetPrompt.OnHidePromptListener() {
                                                                                                                        @Override
                                                                                                                        public void onHidePrompt(
                                                                                                                                MotionEvent event,
                                                                                                                                boolean tappedTarget) {

                                                                                                                        }

                                                                                                                        @Override
                                                                                                                        public void onHidePromptComplete() {
                                                                                                                            TazSettings.getInstance(
                                                                                                                                    getContext())
                                                                                                                                       .setTutorialStepFinished(
                                                                                                                                               "PAPER");

                                                                                                                        }
                                                                                                                    })
                                                                                                            .show();
                }
            }
        } else {

        }
    }

    class ActionModeCallback implements ActionMode.Callback {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            Timber.d("mode: %s, menu: %s", mode, menu);
            if (hasCallback()) getCallback().getRetainData()
                                            .setActionMode(true);
            if (hasCallback()) getCallback().enableDrawer(false);
            actionMode = mode;
            swipeRefresh.setEnabled(false);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            Timber.d("mode: %s, menu: %s", mode, menu);
            menu.clear();
            int countSelected = adapter.getSelected()
                                       .size();
            mode.setTitle(getActivity().getString(R.string.string_library_selected, countSelected));
            mode.getMenuInflater()
                .inflate(R.menu.start_library_selectmode, menu);
            if (countSelected == 0) {
                menu.findItem(R.id.ic_action_download)
                    .setEnabled(false);
                menu.findItem(R.id.ic_action_delete)
                    .setEnabled(false);
            }
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            Timber.d("mode: %s, item: %s", mode, item);
            switch (item.getItemId()) {
                case R.id.ic_action_download:
                    downloadSelected();
                    mode.finish();
                    return true;
                case R.id.ic_action_delete:
                    deleteSelected();
                    mode.finish();
                    return true;
                case R.id.ic_action_selectnone:
                    adapter.deselectAll();
                    mode.invalidate();
                    return true;
                case R.id.ic_action_selectall:
                    adapter.selectAll();
                    mode.invalidate();
                    return true;
                case R.id.ic_action_selectinvert:
                    adapter.selectionInvert();
                    mode.invalidate();
                    return true;
                case R.id.ic_action_selectnotloaded:
                    adapter.selectNotLoaded();
                    mode.invalidate();
                    return true;
            }

            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            Timber.d("mode: %s", mode);
            adapter.deselectAll();
            if (hasCallback()) getCallback().getRetainData()
                                            .setActionMode(false);
            if (hasCallback()) getCallback().enableDrawer(true);
            swipeRefresh.setEnabled(true);
            actionMode = null;
        }

    }

}
