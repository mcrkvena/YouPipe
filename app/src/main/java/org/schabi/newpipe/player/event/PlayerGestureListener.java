package org.schabi.newpipe.player.event;

import static org.schabi.newpipe.ktx.AnimationType.ALPHA;
import static org.schabi.newpipe.ktx.AnimationType.SCALE_AND_ALPHA;
import static org.schabi.newpipe.ktx.ViewUtils.animate;
import static org.schabi.newpipe.player.Player.DEFAULT_CONTROLS_DURATION;
import static org.schabi.newpipe.player.Player.DEFAULT_CONTROLS_HIDE_TIME;
import static org.schabi.newpipe.player.Player.STATE_PLAYING;

import android.app.Activity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.player.MainPlayer;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.helper.PlayerHelper;

/**
 * GestureListener for the player
 *
 * While {@link BasePlayerGestureListener} contains the logic behind the single gestures
 * this class focuses on the visual aspect like hiding and showing the controls or changing
 * volume/brightness during scrolling for specific events.
 */
public class PlayerGestureListener
        extends BasePlayerGestureListener
        implements View.OnTouchListener {
    private static final String TAG = PlayerGestureListener.class.getSimpleName();
    private static final boolean DEBUG = MainActivity.DEBUG;

    private final int maxVolume;

    public PlayerGestureListener(final Player player, final MainPlayer service) {
        super(player, service);
        maxVolume = player.getAudioReactor().getMaxVolume();
    }

    @Override
    public void onDoubleTap(@NonNull final MotionEvent event,
                            @NonNull final DisplayPortion portion) {
        if (DEBUG) {
            Log.d(TAG, "onDoubleTap called with playerType = ["
                    + player.getPlayerType() + "], portion = [" + portion + "]");
        }
        if (player.isSomePopupMenuVisible()) {
            player.hideControls(0, 0);
        }

        if (portion == DisplayPortion.LEFT || portion == DisplayPortion.RIGHT) {
            startMultiDoubleTap(event);
        } else if (portion == DisplayPortion.MIDDLE) {
            player.playPause();
        }
    }

    @Override
    public void onSingleTap(@NonNull final MainPlayer.PlayerType playerType) {
        if (DEBUG) {
            Log.d(TAG, "onSingleTap called with playerType = [" + player.getPlayerType() + "]");
        }

        if (player.isControlsVisible()) {
            player.hideControls(150, 0);
            return;
        }
        // -- Controls are not visible --

        // When player is completed show controls and don't hide them later
        if (player.getCurrentState() == Player.STATE_COMPLETED) {
            player.showControls(0);
        } else {
            player.showControlsThenHide();
        }
    }

    @Override
    public void onScroll(@NonNull final MainPlayer.PlayerType playerType,
                         @NonNull final DisplayPortion portion,
                         @NonNull final MotionEvent initialEvent,
                         @NonNull final MotionEvent movingEvent,
                         final float distanceX, final float distanceY) {
        if (DEBUG) {
            Log.d(TAG, "onScroll called with playerType = ["
                + player.getPlayerType() + "], portion = [" + portion + "]");
        }
        if (playerType == MainPlayer.PlayerType.VIDEO) {

            // -- Brightness and Volume control --
            final boolean isBrightnessGestureEnabled =
                PlayerHelper.isBrightnessGestureEnabled(service);
            final boolean isVolumeGestureEnabled = PlayerHelper.isVolumeGestureEnabled(service);

            if (isBrightnessGestureEnabled && isVolumeGestureEnabled) {
                if (portion == DisplayPortion.LEFT_HALF) {
                    onScrollMainBrightness(distanceX, distanceY);

                } else /* DisplayPortion.RIGHT_HALF */ {
                    onScrollMainVolume(distanceX, distanceY);
                }
            } else if (isBrightnessGestureEnabled) {
                onScrollMainBrightness(distanceX, distanceY);
            } else if (isVolumeGestureEnabled) {
                onScrollMainVolume(distanceX, distanceY);
            }

        } else /* MainPlayer.PlayerType.POPUP */ {

            // -- Determine if the ClosingOverlayView (red X) has to be shown or hidden --
            final View closingOverlayView = player.getClosingOverlayView();
            final boolean showClosingOverlayView = player.isInsideClosingRadius(movingEvent);
            // Check if an view is in expected state and if not animate it into the correct state
            final int expectedVisibility = showClosingOverlayView ? View.VISIBLE : View.GONE;
            if (closingOverlayView.getVisibility() != expectedVisibility) {
                animate(closingOverlayView, showClosingOverlayView, 200);
            }
        }
    }

    private void onScrollMainVolume(final float distanceX, final float distanceY) {
        // If we just started sliding, change the progress bar to match the system volume
        if (player.getVolumeRelativeLayout().getVisibility() != View.VISIBLE) {
            final float volumePercent = player
                    .getAudioReactor().getVolume() / (float) maxVolume;
            player.getVolumeProgressBar().setProgress(
                    (int) (volumePercent * player.getMaxGestureLength()));
        }

        player.getVolumeProgressBar().incrementProgressBy((int) distanceY);
        final float currentProgressPercent = (float) player
                .getVolumeProgressBar().getProgress() / player.getMaxGestureLength();
        final int currentVolume = (int) (maxVolume * currentProgressPercent);
        player.getAudioReactor().setVolume(currentVolume);

        if (DEBUG) {
            Log.d(TAG, "onScroll().volumeControl, currentVolume = " + currentVolume);
        }

        player.getVolumeImageView().setImageDrawable(
                AppCompatResources.getDrawable(service, currentProgressPercent <= 0
                        ? R.drawable.ic_volume_off
                        : currentProgressPercent < 0.25 ? R.drawable.ic_volume_mute
                        : currentProgressPercent < 0.75 ? R.drawable.ic_volume_down
                        : R.drawable.ic_volume_up)
        );

        if (player.getVolumeRelativeLayout().getVisibility() != View.VISIBLE) {
            animate(player.getVolumeRelativeLayout(), true, 200, SCALE_AND_ALPHA);
        }
        if (player.getBrightnessRelativeLayout().getVisibility() == View.VISIBLE) {
            player.getBrightnessRelativeLayout().setVisibility(View.GONE);
        }
    }

    private void onScrollMainBrightness(final float distanceX, final float distanceY) {
        final Activity parent = player.getParentActivity();
        if (parent == null) {
            return;
        }

        final Window window = parent.getWindow();
        final WindowManager.LayoutParams layoutParams = window.getAttributes();
        final ProgressBar bar = player.getBrightnessProgressBar();
        final float oldBrightness = layoutParams.screenBrightness;
        bar.setProgress((int) (bar.getMax() * Math.max(0, Math.min(1, oldBrightness))));
        bar.incrementProgressBy((int) distanceY);

        final float currentProgressPercent = (float) bar.getProgress() / bar.getMax();
        layoutParams.screenBrightness = currentProgressPercent;
        window.setAttributes(layoutParams);

        // Save current brightness level
        PlayerHelper.setScreenBrightness(parent, currentProgressPercent);

        if (DEBUG) {
            Log.d(TAG, "onScroll().brightnessControl, "
                    + "currentBrightness = " + currentProgressPercent);
        }

        player.getBrightnessImageView().setImageDrawable(
                AppCompatResources.getDrawable(service,
                        currentProgressPercent < 0.25
                                ? R.drawable.ic_brightness_low
                                : currentProgressPercent < 0.75
                                ? R.drawable.ic_brightness_medium
                                : R.drawable.ic_brightness_high)
        );

        if (player.getBrightnessRelativeLayout().getVisibility() != View.VISIBLE) {
            animate(player.getBrightnessRelativeLayout(), true, 200, SCALE_AND_ALPHA);
        }
        if (player.getVolumeRelativeLayout().getVisibility() == View.VISIBLE) {
            player.getVolumeRelativeLayout().setVisibility(View.GONE);
        }
    }

    @Override
    public void onScrollEnd(@NonNull final MainPlayer.PlayerType playerType,
                            @NonNull final MotionEvent event) {
        if (DEBUG) {
            Log.d(TAG, "onScrollEnd called with playerType = ["
                + player.getPlayerType() + "]");
        }

        if (player.isControlsVisible() && player.getCurrentState() == STATE_PLAYING) {
            player.hideControls(DEFAULT_CONTROLS_DURATION, DEFAULT_CONTROLS_HIDE_TIME);
        }

        if (playerType == MainPlayer.PlayerType.VIDEO) {
            if (player.getVolumeRelativeLayout().getVisibility() == View.VISIBLE) {
                animate(player.getVolumeRelativeLayout(), false, 200, SCALE_AND_ALPHA,
                        200);
            }
            if (player.getBrightnessRelativeLayout().getVisibility() == View.VISIBLE) {
                animate(player.getBrightnessRelativeLayout(), false, 200, SCALE_AND_ALPHA,
                        200);
            }
        } else /* Popup-Player */ {
            if (player.isInsideClosingRadius(event)) {
                player.closePopup();
            } else if (!player.isPopupClosing()) {
                animate(player.getCloseOverlayButton(), false, 200);
                animate(player.getClosingOverlayView(), false, 200);
            }
        }
    }

    @Override
    public void onPopupResizingStart() {
        if (DEBUG) {
            Log.d(TAG, "onPopupResizingStart called");
        }
        player.getLoadingPanel().setVisibility(View.GONE);

        player.hideControls(0, 0);
        animate(player.getFastSeekOverlay(), false, 0);
        animate(player.getCurrentDisplaySeek(), false, 0, ALPHA, 0);
    }

    @Override
    public void onPopupResizingEnd() {
        if (DEBUG) {
            Log.d(TAG, "onPopupResizingEnd called");
        }
    }
}


