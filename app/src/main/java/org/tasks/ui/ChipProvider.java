package org.tasks.ui;

import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;
import static com.todoroo.andlib.utility.AndroidUtilities.assertMainThread;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import androidx.annotation.LayoutRes;
import androidx.core.content.res.ResourcesCompat;
import com.google.android.material.chip.Chip;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.Ordering;
import com.todoroo.astrid.api.CaldavFilter;
import com.todoroo.astrid.api.Filter;
import com.todoroo.astrid.api.GtasksFilter;
import com.todoroo.astrid.api.TagFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.tasks.LocalBroadcastManager;
import org.tasks.R;
import org.tasks.billing.Inventory;
import org.tasks.data.CaldavCalendar;
import org.tasks.data.CaldavDao;
import org.tasks.data.GoogleTaskList;
import org.tasks.data.GoogleTaskListDao;
import org.tasks.data.TagData;
import org.tasks.data.TagDataDao;
import org.tasks.injection.ApplicationScope;
import org.tasks.injection.ForApplication;
import org.tasks.themes.ThemeCache;
import org.tasks.themes.ThemeColor;

@ApplicationScope
public class ChipProvider {

  private final Map<String, GtasksFilter> googleTaskLists = new HashMap<>();
  private final Map<String, CaldavFilter> caldavCalendars = new HashMap<>();
  private final Map<String, TagFilter> tagDatas = new HashMap<>();
  private final Inventory inventory;
  private final ThemeCache themeCache;
  private final int iconAlpha;
  private final LocalBroadcastManager localBroadcastManager;
  private final Ordering<TagFilter> orderByName =
      new Ordering<TagFilter>() {
        @Override
        public int compare(TagFilter left, TagFilter right) {
          return left.listingTitle.compareTo(right.listingTitle);
        }
      };

  @Inject
  public ChipProvider(
      @ForApplication Context context,
      Inventory inventory,
      ThemeCache themeCache,
      GoogleTaskListDao googleTaskListDao,
      CaldavDao caldavDao,
      TagDataDao tagDataDao,
      LocalBroadcastManager localBroadcastManager) {
    this.inventory = inventory;
    this.themeCache = themeCache;
    this.localBroadcastManager = localBroadcastManager;
    iconAlpha =
        (int) (255 * ResourcesCompat.getFloat(context.getResources(), R.dimen.alpha_secondary));

    googleTaskListDao.subscribeToLists().observeForever(this::updateGoogleTaskLists);
    caldavDao.subscribeToCalendars().observeForever(this::updateCaldavCalendars);
    tagDataDao.subscribeToTags().observeForever(this::updateTags);
  }

  private void updateGoogleTaskLists(List<GoogleTaskList> updated) {
    googleTaskLists.clear();
    for (GoogleTaskList update : updated) {
      googleTaskLists.put(update.getRemoteId(), new GtasksFilter(update));
    }
    localBroadcastManager.broadcastRefresh();
  }

  private void updateCaldavCalendars(List<CaldavCalendar> updated) {
    caldavCalendars.clear();
    for (CaldavCalendar update : updated) {
      caldavCalendars.put(update.getUuid(), new CaldavFilter(update));
    }
    localBroadcastManager.broadcastRefresh();
  }

  private void updateTags(List<TagData> updated) {
    tagDatas.clear();
    for (TagData update : updated) {
      tagDatas.put(update.getRemoteId(), new TagFilter(update));
    }
    localBroadcastManager.broadcastRefresh();
  }

  public List<Chip> getChips(
      Activity activity, String caldav, String googleTask, Iterable<String> tagUuids) {
    assertMainThread();

    List<Chip> chips = new ArrayList<>();
    if (!Strings.isNullOrEmpty(googleTask)) {
      GtasksFilter googleTaskFilter = googleTaskLists.get(googleTask);
      if (googleTaskFilter != null) {
        chips.add(newTagChip(activity, googleTaskFilter));
      }
    } else if (!Strings.isNullOrEmpty(caldav)) {
      CaldavFilter caldavFilter = caldavCalendars.get(caldav);
      if (caldavFilter != null) {
        chips.add(newTagChip(activity, caldavFilter));
      }
    }
    Iterable<TagFilter> tagFilters =
        filter(transform(tagUuids, tagDatas::get), Predicates.notNull());
    for (TagFilter tagFilter : orderByName.sortedCopy(tagFilters)) {
      chips.add(newTagChip(activity, tagFilter));
    }

    return chips;
  }

  public void apply(Chip chip, Filter filter) {
    apply(chip, filter.listingTitle, filter.tint);
  }

  public void apply(Chip chip, TagData tagData) {
    apply(chip, tagData.getName(), tagData.getColor());
  }

  private Chip newTagChip(Activity activity, Filter filter) {
    Chip chip = newChip(activity, R.layout.chip_tag, filter);
    apply(chip, filter.listingTitle, filter.tint);
    return chip;
  }

  public Chip newClosableChip(Activity activity, Object tag) {
    Chip chip = (Chip) activity.getLayoutInflater().inflate(R.layout.chip_closable, null);
    chip.setTag(tag);
    return chip;
  }

  private Chip newChip(Activity activity, @LayoutRes int layout, Object tag) {
    Chip chip = (Chip) activity.getLayoutInflater().inflate(layout, null);
    chip.setTag(tag);
    return chip;
  }

  private void apply(Chip chip, String name, int theme) {
    ThemeColor color = getColor(theme);
    chip.setText(name);
    chip.setCloseIconTint(
        new ColorStateList(new int[][] {new int[] {}}, new int[] {color.getActionBarTint()}));
    chip.setTextColor(color.getActionBarTint());
    chip.getChipDrawable().setAlpha(iconAlpha);
    chip.setChipBackgroundColor(
        new ColorStateList(
            new int[][] {
              new int[] {-android.R.attr.state_checked}, new int[] {android.R.attr.state_checked}
            },
            new int[] {color.getPrimaryColor(), color.getPrimaryColor()}));
  }

  private ThemeColor getColor(int theme) {
    if (theme >= 0) {
      ThemeColor color = themeCache.getThemeColor(theme);
      if (color.isFree() || inventory.purchasedThemes()) {
        return color;
      }
    }
    return themeCache.getUntaggedColor();
  }
}
