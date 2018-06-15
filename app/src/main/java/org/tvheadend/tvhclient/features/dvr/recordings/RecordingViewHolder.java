package org.tvheadend.tvhclient.features.dvr.recordings;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.widget.TextViewCompat;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import org.tvheadend.tvhclient.R;
import org.tvheadend.tvhclient.data.entity.Recording;
import org.tvheadend.tvhclient.features.shared.UIUtils;
import org.tvheadend.tvhclient.features.shared.callbacks.RecyclerViewClickCallback;

import butterknife.BindView;
import butterknife.ButterKnife;

public class RecordingViewHolder extends RecyclerView.ViewHolder {

    @BindView(R.id.icon)
    ImageView iconImageView;
    @BindView(R.id.icon_text)
    TextView iconTextView;
    @BindView(R.id.title)
    TextView titleTextView;
    @BindView(R.id.subtitle)
    TextView subtitleTextView;
    @BindView(R.id.summary)
    TextView summaryTextView;
    @BindView(R.id.is_series_recording)
    TextView isSeriesRecordingTextView;
    @BindView(R.id.is_timer_recording)
    TextView isTimerRecordingTextView;
    @BindView(R.id.channel)
    TextView channelTextView;
    @BindView(R.id.start)
    TextView startTimeTextView;
    @BindView(R.id.stop)
    TextView stopTimeTextView;
    @BindView(R.id.date)
    TextView dateTextView;
    @BindView(R.id.duration)
    TextView durationTextView;
    @Nullable
    @BindView(R.id.state)
    ImageView stateImageView;
    @BindView(R.id.description)
    TextView descriptionTextView;
    @BindView(R.id.failed_reason)
    TextView failedReasonTextView;
    @BindView(R.id.enabled)
    TextView isEnabledTextView;
    @Nullable
    @BindView(R.id.dual_pane_list_item_selection)
    ImageView dualPaneListItemSelection;

    RecordingViewHolder(View view) {
        super(view);
        ButterKnife.bind(this, view);
    }

    public void bindData(Context context, final Recording recording, boolean selected, int htspVersion, RecyclerViewClickCallback clickCallback) {
        itemView.setTag(recording);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean playOnChannelIcon = sharedPreferences.getBoolean("channel_icon_starts_playback_enabled", true);
        boolean lightTheme = sharedPreferences.getBoolean("light_theme_enabled", true);

        itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clickCallback.onClick(view, getAdapterPosition());
            }
        });
        itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                clickCallback.onLongClick(view, getAdapterPosition());
                return true;
            }
        });
        iconImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (playOnChannelIcon && recording != null
                        && (recording.isCompleted() || recording.isRecording())) {
                    clickCallback.onClick(view, getAdapterPosition());
                }
            }
        });

        if (dualPaneListItemSelection != null) {
            // Set the correct indication when the dual pane mode is active
            // If the item is selected the the arrow will be shown, otherwise
            // only a vertical separation line is displayed.
            if (selected) {
                final int icon = (lightTheme) ? R.drawable.dual_pane_selector_active_light : R.drawable.dual_pane_selector_active_dark;
                dualPaneListItemSelection.setBackgroundResource(icon);
            } else {
                final int icon = R.drawable.dual_pane_selector_inactive;
                dualPaneListItemSelection.setBackgroundResource(icon);
            }
        }

        titleTextView.setText(recording.getTitle());

        subtitleTextView.setVisibility(!TextUtils.isEmpty(recording.getSubtitle()) ? View.VISIBLE : View.GONE);
        subtitleTextView.setText(recording.getSubtitle());

        channelTextView.setText(recording.getChannelName());

        TextViewCompat.setAutoSizeTextTypeWithDefaults(iconTextView, TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);
        iconTextView.setText(recording.getChannelName());

        // Show the channel icon if available and set in the preferences.
        // If not chosen, hide the imageView and show the channel name.
        Picasso.get()
                .load(UIUtils.getIconUrl(context, recording.getChannelIcon()))
                .into(iconImageView, new Callback() {
                    @Override
                    public void onSuccess() {
                        iconTextView.setVisibility(View.INVISIBLE);
                        iconImageView.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onError(Exception e) {

                    }
                });

        dateTextView.setText(UIUtils.getDate(context, recording.getStart()));

        startTimeTextView.setText(UIUtils.getTimeText(context, recording.getStart()));
        stopTimeTextView.setText(UIUtils.getTimeText(context, recording.getStop()));

        String durationTime = context.getString(R.string.minutes, recording.getDuration());
        durationTextView.setText(durationTime);

        summaryTextView.setVisibility(!TextUtils.isEmpty(recording.getSummary()) ? View.VISIBLE : View.GONE);
        summaryTextView.setText(recording.getSummary());

        descriptionTextView.setVisibility(!TextUtils.isEmpty(recording.getDescription()) ? View.VISIBLE : View.GONE);
        descriptionTextView.setText(recording.getDescription());

        String failedReasonText = UIUtils.getRecordingFailedReasonText(context, recording);
        failedReasonTextView.setVisibility(!TextUtils.isEmpty(failedReasonText) ? View.VISIBLE : View.GONE);
        failedReasonTextView.setText(failedReasonText);

        // Show only the recording icon
        if (stateImageView != null) {
            if (recording.isRecording()) {
                stateImageView.setImageResource(R.drawable.ic_rec_small);
                stateImageView.setVisibility(ImageView.VISIBLE);
            } else {
                stateImageView.setVisibility(ImageView.GONE);
            }
        }

        // Show the information if the recording belongs to a series recording
        if (!TextUtils.isEmpty(recording.getAutorecId())) {
            isSeriesRecordingTextView.setVisibility(ImageView.VISIBLE);
        } else {
            isSeriesRecordingTextView.setVisibility(ImageView.GONE);
        }

        // Show the information if the recording belongs to a series recording
        if (!TextUtils.isEmpty(recording.getTimerecId())) {
            isTimerRecordingTextView.setVisibility(ImageView.VISIBLE);
        } else {
            isTimerRecordingTextView.setVisibility(ImageView.GONE);
        }

        isEnabledTextView.setVisibility((htspVersion >= 19 && recording.getEnabled() > 0) ? View.VISIBLE : View.GONE);
        isEnabledTextView.setText(recording.getEnabled() > 0 ? R.string.recording_enabled : R.string.recording_disabled);
    }
}