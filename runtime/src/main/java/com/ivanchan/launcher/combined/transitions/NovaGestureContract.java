/*
 * GestureNavContract transport adapted from the Android Open Source Project (2020),
 * licensed under the Apache License, Version 2.0: https://www.apache.org/licenses/LICENSE-2.0
 * Cross-checked against Nova 8.9.2 o9/p1, x7/u, FloatingSurfaceView.X and ea/a.
 */
package com.ivanchan.launcher.combined.transitions;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.UserHandle;
import android.view.SurfaceControl;
import java.lang.ref.WeakReference;

/**
 * Purpose: Port Nova's real system-gesture handshake, not an invented return animation.
 * Invocation: Consume the HOME intent before the vendor reuses p1; reply when the icon surface exists.
 * Contract: API30+, exact parcel keys/types, original Message routing identity preserved. A single
 * weak finish receiver avoids Binder/Activity leaks; arg1 additionally rejects stale-session finish.
 * No app identity, message or pixels enter diagnostics. No local motion clock belongs to this class.
 * Verification: Nova/AOSP raw contract and source fixtures; Binder/OEM execution remains unverified.
 * Visual: Supplies the target to the system that moves the actual app window and icon surface.
 */
final class NovaGestureContract {
    static final String EXTRA = "gesture_nav_contract_v1";
    static final String POSITION = "gesture_nav_contract_icon_position";
    static final String SURFACE = "gesture_nav_contract_surface_control";
    static final String FINISH = "gesture_nav_contract_finish_callback";
    static final String REMOTE = "android.intent.extra.REMOTE_CALLBACK";
    final ComponentName component;
    final UserHandle user;
    private final Message callback;
    private static final FinishReceiver FINISH_RECEIVER = new FinishReceiver();

    private NovaGestureContract(ComponentName component, UserHandle user, Message callback) {
        this.component = component;
        this.user = user;
        this.callback = Message.obtain();
        this.callback.copyFrom(callback);
    }

    /** Purpose: Match Nova's one-shot intent consumption and null/replyTo validation.
     * Invocation: Activity creation or onNewIntent entry, before vendor parameter reuse.
     * Contract: Absent, unsupported or malformed contracts never become fabricated animations.
     * Verification: Exact key/type/gate source checks; no parcel IPC has run in this source-only pass.
     */
    @SuppressWarnings("deprecation")
    static NovaGestureContract consume(Intent intent) {
        if (Build.VERSION.SDK_INT < 30 || intent == null
                || !Intent.ACTION_MAIN.equals(intent.getAction())) return null;
        try {
            Bundle data = intent.getBundleExtra(EXTRA);
            if (data == null) return null;
            intent.removeExtra(EXTRA);
            Object component = data.getParcelable(Intent.EXTRA_COMPONENT_NAME);
            Object user = data.getParcelable(Intent.EXTRA_USER);
            Object callback = data.getParcelable(REMOTE);
            if (!(component instanceof ComponentName) || !(user instanceof UserHandle)
                    || !(callback instanceof Message) || ((Message) callback).replyTo == null) {
                TransitionDiagnostics.event("gesture_contract_malformed", 0);
                return null;
            }
            TransitionDiagnostics.event("gesture_contract_received", 0);
            return new NovaGestureContract((ComponentName) component, (UserHandle) user,
                    (Message) callback);
        } catch (RuntimeException malformedParcel) {
            TransitionDiagnostics.event("gesture_contract_unreadable", 0);
            return null;
        }
    }

    /** Purpose: Send Nova's RectF/surface/finish tuple to the original system Messenger.
     * Invocation: Surface creation or destination change; never replace callback.obj or replyTo.
     * Contract: Only our own icon SurfaceControl is shared, never another app's captured pixels.
     * Verification: Exact fields and copyFrom ordering checked; remote acceptance needs a device.
     */
    boolean send(RectF position, SurfaceControl surface, NovaGestureSurface owner) {
        Bundle data = new Bundle();
        data.putParcelable(POSITION, new RectF(position));
        data.putParcelable(SURFACE, surface);
        data.putParcelable(FINISH, FINISH_RECEIVER.forSurface(owner));
        Message reply = Message.obtain();
        reply.copyFrom(callback);
        reply.setData(data);
        try {
            reply.replyTo.send(reply);
            return true;
        } catch (RemoteException | RuntimeException unavailable) {
            TransitionDiagnostics.event("gesture_reply_failed", owner.sessionId);
            return false;
        }
    }

    static void release(NovaGestureSurface owner) { FINISH_RECEIVER.release(owner); }

    /** Purpose: Nova's static, weak what=0 finish callback with a generation guard.
     * Invocation: The system returns the finish Message after its task/icon animation completes.
     * Contract: Old completion cannot close a new gesture; there is no guessed completion timer.
     * Verification: Source protocol tests, not a claim of exercised Binder delivery.
     */
    private static final class FinishReceiver implements Handler.Callback {
        private final Messenger messenger = new Messenger(new Handler(Looper.getMainLooper(), this));
        private WeakReference<NovaGestureSurface> current = new WeakReference<>(null);
        Message forSurface(NovaGestureSurface surface) {
            current = new WeakReference<>(surface);
            Message finish = Message.obtain();
            finish.what = 0;
            finish.arg1 = surface.sessionId;
            finish.replyTo = messenger;
            return finish;
        }
        void release(NovaGestureSurface surface) {
            if (current.get() == surface) current.clear();
        }
        @Override public boolean handleMessage(Message message) {
            if (message.what != 0) return false;
            NovaGestureSurface surface = current.get();
            if (surface != null && message.arg1 == surface.sessionId) surface.systemFinished();
            return true;
        }
    }
}
