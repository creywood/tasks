package org.tasks.caldav;

import static android.text.TextUtils.isEmpty;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProviders;
import at.bitfire.dav4jvm.exception.HttpException;
import butterknife.BindView;
import butterknife.OnTextChanged;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.rey.material.widget.ProgressView;
import com.todoroo.astrid.activity.MainActivity;
import com.todoroo.astrid.activity.TaskListFragment;
import com.todoroo.astrid.api.CaldavFilter;
import com.todoroo.astrid.helper.UUIDHelper;
import com.todoroo.astrid.service.TaskDeleter;
import java.net.ConnectException;
import javax.inject.Inject;
import org.tasks.R;
import org.tasks.activities.CreateCalendarViewModel;
import org.tasks.activities.DeleteCalendarViewModel;
import org.tasks.data.CaldavAccount;
import org.tasks.data.CaldavCalendar;
import org.tasks.data.CaldavDao;
import org.tasks.injection.ActivityComponent;
import org.tasks.sync.SyncAdapters;
import org.tasks.ui.DisplayableException;

public class CaldavCalendarSettingsActivity extends BaseListSettingsActivity {

  public static final String EXTRA_CALDAV_CALENDAR = "extra_caldav_calendar";
  public static final String EXTRA_CALDAV_ACCOUNT = "extra_caldav_account";
  @Inject CaldavDao caldavDao;
  @Inject SyncAdapters syncAdapters;
  @Inject TaskDeleter taskDeleter;
  @Inject CaldavClient client;

  @BindView(R.id.root_layout)
  LinearLayout root;

  @BindView(R.id.name)
  TextInputEditText name;

  @BindView(R.id.name_layout)
  TextInputLayout nameLayout;

  @BindView(R.id.progress_bar)
  ProgressView progressView;

  private CaldavCalendar caldavCalendar;
  private CaldavAccount caldavAccount;
  private CreateCalendarViewModel createCalendarViewModel;
  private DeleteCalendarViewModel deleteCalendarViewModel;

  @Override
  protected int getLayout() {
    return R.layout.activity_caldav_calendar_settings;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    Intent intent = getIntent();
    caldavCalendar = intent.getParcelableExtra(EXTRA_CALDAV_CALENDAR);

    super.onCreate(savedInstanceState);

    createCalendarViewModel = ViewModelProviders.of(this).get(CreateCalendarViewModel.class);
    deleteCalendarViewModel = ViewModelProviders.of(this).get(DeleteCalendarViewModel.class);

    if (caldavCalendar == null) {
      caldavAccount = intent.getParcelableExtra(EXTRA_CALDAV_ACCOUNT);
    } else {
      caldavAccount = caldavDao.getAccountByUuid(caldavCalendar.getAccount());
      nameLayout.setVisibility(View.GONE);
    }
    caldavAccount =
        caldavCalendar == null
            ? intent.getParcelableExtra(EXTRA_CALDAV_ACCOUNT)
            : caldavDao.getAccountByUuid(caldavCalendar.getAccount());

    if (savedInstanceState == null) {
      if (caldavCalendar != null) {
        name.setText(caldavCalendar.getName());
        selectedTheme = caldavCalendar.getColor();
        selectedIcon = caldavCalendar.getIcon();
      }
    }

    if (caldavCalendar == null) {
      name.requestFocus();
      InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
      imm.showSoftInput(name, InputMethodManager.SHOW_IMPLICIT);
    }

    createCalendarViewModel.observe(this, this::createSuccessful, this::requestFailed);
    deleteCalendarViewModel.observe(this, this::onDeleted, this::requestFailed);

    updateTheme();
  }

  @Override
  protected boolean isNew() {
    return caldavCalendar == null;
  }

  @Override
  protected String getToolbarTitle() {
    return isNew() ? getString(R.string.new_list) : caldavCalendar.getName();
  }

  @OnTextChanged(R.id.name)
  void onNameChanged(CharSequence text) {
    nameLayout.setError(null);
  }

  @Override
  public void inject(ActivityComponent component) {
    component.inject(this);
  }

  @Override
  protected void save() {
    if (requestInProgress()) {
      return;
    }

    String name = getNewName();

    if (isEmpty(name)) {
      nameLayout.setError(getString(R.string.name_cannot_be_empty));
      return;
    }

    if (caldavCalendar == null) {
      showProgressIndicator();
      createCalendarViewModel.createCalendar(client, caldavAccount, name);
    } else if (hasChanges()) {
      updateAccount();
    } else {
      finish();
    }
  }

  private void showProgressIndicator() {
    progressView.setVisibility(View.VISIBLE);
  }

  private void hideProgressIndicator() {
    progressView.setVisibility(View.GONE);
  }

  private boolean requestInProgress() {
    return progressView.getVisibility() == View.VISIBLE;
  }

  private void requestFailed(Throwable t) {
    hideProgressIndicator();

    if (t instanceof HttpException) {
      showSnackbar(t.getMessage());
    } else if (t instanceof DisplayableException) {
      showSnackbar(((DisplayableException) t).getResId());
    } else if (t instanceof ConnectException) {
      showSnackbar(R.string.network_error);
    } else {
      showGenericError();
    }
  }

  private void showGenericError() {
    showSnackbar(R.string.error_adding_account);
  }

  private void showSnackbar(int resId) {
    showSnackbar(getString(resId));
  }

  private void showSnackbar(String message) {
    Snackbar snackbar =
        Snackbar.make(root, message, 8000)
            .setTextColor(ContextCompat.getColor(this, R.color.snackbar_text_color))
            .setActionTextColor(ContextCompat.getColor(this, R.color.snackbar_action_color));
    snackbar
        .getView()
        .setBackgroundColor(ContextCompat.getColor(this, R.color.snackbar_background));
    snackbar.show();
  }

  private void createSuccessful(String url) {
    CaldavCalendar caldavCalendar = new CaldavCalendar();
    caldavCalendar.setUuid(UUIDHelper.newUUID());
    caldavCalendar.setAccount(caldavAccount.getUuid());
    caldavCalendar.setUrl(url);
    caldavCalendar.setName(getNewName());
    caldavCalendar.setColor(selectedTheme);
    caldavCalendar.setId(caldavDao.insert(caldavCalendar));
    caldavCalendar.setIcon(selectedIcon);
    setResult(
        RESULT_OK,
        new Intent().putExtra(MainActivity.OPEN_FILTER, new CaldavFilter(caldavCalendar)));
    finish();
  }

  private void updateAccount() {
    caldavCalendar.setName(getNewName());
    caldavCalendar.setColor(selectedTheme);
    caldavCalendar.setIcon(selectedIcon);
    caldavDao.update(caldavCalendar);
    setResult(
        RESULT_OK,
        new Intent(TaskListFragment.ACTION_RELOAD)
            .putExtra(MainActivity.OPEN_FILTER, new CaldavFilter(caldavCalendar)));
    finish();
  }

  @Override
  protected boolean hasChanges() {
    if (caldavCalendar == null) {
      return !isEmpty(getNewName()) || selectedTheme != -1 || selectedIcon != -1;
    }
    return !caldavCalendar.getName().equals(getNewName())
        || selectedIcon != caldavCalendar.getIcon()
        || selectedTheme != caldavCalendar.getColor();
  }

  private String getNewName() {
    return name.getText().toString().trim();
  }

  @Override
  public void finish() {
    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    imm.hideSoftInputFromWindow(name.getWindowToken(), 0);
    super.finish();
  }

  @Override
  protected void discard() {
    if (!requestInProgress()) {
      super.discard();
    }
  }

  @Override
  protected void promptDelete() {
    if (!requestInProgress()) {
      super.promptDelete();
    }
  }

  @Override
  protected void delete() {
    showProgressIndicator();
    deleteCalendarViewModel.deleteCalendar(client, caldavAccount, caldavCalendar);
  }

  private void onDeleted(boolean deleted) {
    if (deleted) {
      taskDeleter.delete(caldavCalendar);
      setResult(RESULT_OK, new Intent(TaskListFragment.ACTION_DELETED));
      finish();
    }
  }
}
