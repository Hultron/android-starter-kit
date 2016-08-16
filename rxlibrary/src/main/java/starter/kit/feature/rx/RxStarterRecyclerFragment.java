package starter.kit.feature.rx;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.FrameLayout;
import butterknife.ButterKnife;
import com.paginate.Paginate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import rx.Observable;
import rx.functions.Action1;
import starter.kit.feature.StarterContentFragment;
import starter.kit.feature.StarterFragConfig;
import starter.kit.model.EmptyEntity;
import starter.kit.model.entity.Entity;
import starter.kit.retrofit.ErrorResponse;
import starter.kit.rx.R;
import starter.kit.util.ErrorHandler;
import starter.kit.util.ProgressInterface;
import starter.kit.util.RxIdentifier;
import starter.kit.util.RxPager;
import starter.kit.util.RxRequestKey;
import starter.kit.viewholder.StarterEmptyViewHolder;
import support.ui.adapters.BaseEasyViewHolderFactory;
import support.ui.adapters.EasyRecyclerAdapter;
import support.ui.adapters.EasyViewHolder;

import static rx.android.schedulers.AndroidSchedulers.mainThread;

public abstract class RxStarterRecyclerFragment
    extends StarterContentFragment<RxResourcePresenter>
    implements com.paginate.Paginate.Callbacks,
    ProgressInterface,
    SwipeRefreshLayout.OnRefreshListener {

  FrameLayout mContainer;
  SwipeRefreshLayout mSwipeRefreshLayout;
  RecyclerView mRecyclerView;

  private EasyRecyclerAdapter mAdapter;
  private Paginate mPaginate;
  private StarterFragConfig mFragConfig;

  private RxRequestKey mRequestKey;

  public RxRequestKey getRequestKey() {
    return mRequestKey;
  }

  protected void buildFragConfig(StarterFragConfig fragConfig) {
    if (fragConfig == null) return;

    mFragConfig = fragConfig;

    if (fragConfig.isWithIdentifierRequest()) {
      mRequestKey = buildRxIdentifier(fragConfig);
    } else {
      mRequestKey = buildRxPager(fragConfig);
    }

    BaseEasyViewHolderFactory viewHolderFactory = fragConfig.getViewHolderFactory();
    if (viewHolderFactory != null) {
      mAdapter.viewHolderFactory(viewHolderFactory);
    }

    //noinspection unchecked
    HashMap<Class, Class<? extends EasyViewHolder>> boundViewHolders = fragConfig.getBoundViewHolders();
    if (!boundViewHolders.isEmpty()) {
      for (Map.Entry<Class, Class<? extends EasyViewHolder>> entry : boundViewHolders.entrySet()) {
        mAdapter.bind(entry.getKey(), entry.getValue());
      }
    }
    // bind empty value
    mAdapter.bind(EmptyEntity.class, StarterEmptyViewHolder.class);
  }

  private RxRequestKey buildRxIdentifier(StarterFragConfig fragConfig) {
    return new RxIdentifier(fragConfig.getPageSize(), new Action1<RxIdentifier>() {
      @Override public void call(RxIdentifier rxIdentifier) {
        getPresenter().requestNext(rxIdentifier);
      }
    });
  }

  private RxRequestKey buildRxPager(StarterFragConfig fragConfig) {
    return new RxPager(fragConfig.getStartPage(), fragConfig.getPageSize(), new Action1<RxPager>() {
      @Override public void call(RxPager pager) {
        getPresenter().requestNext(pager);
      }
    });
  }

  @Override public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    mAdapter = new EasyRecyclerAdapter(getContext());
  }

  @Override protected int getFragmentLayout() {
    return R.layout.starter_recycler_view;
  }

  @Override public View provideContentView() {
    return mSwipeRefreshLayout;
  }

  @Override public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    mContainer = ButterKnife.findById(view, R.id.support_ui_content_container);
    mSwipeRefreshLayout = ButterKnife.findById(view, R.id.swipeRefreshLayout);
    mRecyclerView = ButterKnife.findById(view, R.id.support_ui_content_recycler_view);

    setupRecyclerView();
    setupPaginate();
    setupSwipeRefreshLayout();
  }

  private void setupSwipeRefreshLayout() {
    if (mFragConfig != null) {
      int[] colors = mFragConfig.getColorSchemeColors();
      if (colors != null) {
        mSwipeRefreshLayout.setColorSchemeColors(colors);
      }
      boolean enabled = mFragConfig.isEnabled();
      mSwipeRefreshLayout.setEnabled(enabled);
      if (enabled) {
        mSwipeRefreshLayout.setOnRefreshListener(this);
      }
    }
  }

  private void setupPaginate() {
    if (mFragConfig != null) {
      if (mFragConfig.canAddLoadingListItem()) {

        mPaginate = Paginate.with(mRecyclerView, this)
            .setLoadingTriggerThreshold(mFragConfig.getLoadingTriggerThreshold())
            .addLoadingListItem(true)
            .setLoadingListItemCreator(mFragConfig.getLoadingListItemCreator())
            .setLoadingListItemSpanSizeLookup(() -> mFragConfig.getSpanSizeLookup())
            .build();

        mPaginate.setHasMoreDataToLoad(false);
      }
    }
  }

  private void setupRecyclerView() {
    mRecyclerView.setAdapter(mAdapter);

    if (mFragConfig != null) {
      RecyclerView.LayoutManager layoutManager = mFragConfig.getLayoutManager();
      if (layoutManager != null) {
        mRecyclerView.setLayoutManager(layoutManager);
      } else {
        mRecyclerView.setLayoutManager(newLayoutManager());
      }

      RecyclerView.ItemDecoration decor = mFragConfig.getDecor();
      if (decor != null) {
        mRecyclerView.addItemDecoration(decor);
      }

      RecyclerView.ItemAnimator animator = mFragConfig.getAnimator();
      if (animator != null) {
        mRecyclerView.setItemAnimator(animator);
      }
    }
  }

  private RecyclerView.LayoutManager newLayoutManager() {
    return new LinearLayoutManager(getContext());
  }

  @Override public void showProgress() {
    Observable.empty().observeOn(mainThread()).doOnTerminate(() -> {
      if (isEmpty()) {
        getContentPresenter().displayLoadView();
      } else if (mRequestKey != null && mRequestKey.isFirstPage()) {
        mSwipeRefreshLayout.setRefreshing(true);
      } else if (mPaginate != null) {
        mPaginate.setHasMoreDataToLoad(true);
      }
    }).subscribe();
  }

  @Override public void hideProgress() {
    Observable.empty().observeOn(mainThread())
        .doOnTerminate(() -> {
          if (isNotNull(mSwipeRefreshLayout)) {
            mSwipeRefreshLayout.setRefreshing(false);
          }
        })
        .subscribe();
  }

  public void notifyDataSetChanged(ArrayList<? extends Entity> items) {
    if (mRequestKey.isFirstPage()) {
      mAdapter.clear();
    }

    mAdapter.appendAll(items);
    mRequestKey.received(items);

    if (isNotNull(mPaginate)) {
      mPaginate.setHasMoreDataToLoad(false);
    }

    if (isEmpty()) {
      getContentPresenter().displayEmptyView();
    } else {
      getContentPresenter().displayContentView();
    }

  }

  public void onError(Throwable throwable) {
    ErrorResponse errorResponse = ErrorHandler.handleThrowable(throwable);

    if (mRequestKey.isFirstPage() && mAdapter.isEmpty()) {
      mAdapter.clear();
    }

    if (mPaginate != null) {
      mPaginate.setHasMoreDataToLoad(false);
    }

    if (isEmpty()) {
      getContentPresenter().displayErrorView();
    }
  }

  @Override public void onResume() {
    super.onResume();
    if (isNotNull(mRequestKey) && !mRequestKey.requested()) {
      getPresenter().request();
    }
  }

  @Override public void onDestroyView() {
    super.onDestroyView();
    mSwipeRefreshLayout = null;
    mRecyclerView = null;
  }

  @Override public void onDestroy() {
    super.onDestroy();
    mAdapter = null;
    mFragConfig = null;
    mPaginate = null;
  }

  @Override public void onRefresh() {
    mRequestKey.reset();
    getPresenter().request();
  }

  // Paginate delegate
  @Override public void onLoadMore() {
    if (isNotNull(mPaginate) && isNotNull(mRequestKey)
        && !mAdapter.isEmpty()
        && mRequestKey.hasMoreData()) {
      mPaginate.setHasMoreDataToLoad(true);
      mRequestKey.next();
    }
  }

  @Override public boolean isLoading() {
    return mSwipeRefreshLayout.isRefreshing();
  }

  @Override public boolean hasLoadedAllItems() {
    return mRequestKey != null && !mRequestKey.hasMoreData();
  }

  @Override public void onEmptyViewClick(View view) {
    onRefresh();
  }

  @Override public void onErrorViewClick(View view) {
    onRefresh();
  }

  public boolean isEmpty() {
    return isNotNull(mAdapter) && mAdapter.isEmpty();
  }

  public boolean isNotNull(Object object) {
    return object != null;
  }
}
