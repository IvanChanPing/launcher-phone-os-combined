package com.ivanchan.launcher.combined.transitions;

import android.app.Activity;
import android.content.Intent;
import android.content.ComponentName;
import android.os.UserHandle;
import java.util.Objects;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Purpose: Version-specific, read-only bridge to Launcher Phone OS 1.4.1's existing view owners.
 * Invocation: Controller preparation and replay; package preparation locks the original owners.
 * Contract: No vendor objects are replaced. Public target methods and raw cell fields a/b are
 * verified against the decode. Failures propagate to the controller's observable native fallback.
 * Geometry includes every ancestor matrix and scroll. Folder previews are one item, not sub-icons.
 * Verification: Exact-target source-contract checks; Android binding remains compile/device-unverified.
 * Visual: Supplies the current workspace, folder, library or search surface in drag-layer coordinates.
 */
final class LauncherAccess {
    static final String LAUNCHER = "com.android.launcher3.Launcher";
    static final String BUBBLE = "com.android.launcher3.BubbleTextView";
    static final String FOLDER_ICON = "com.android.launcher3.folder.FolderIcon";
    static final String FOLDER = "com.android.launcher3.folder.Folder";
    static final String CELL = "com.android.launcher3.CellLayout";

    static boolean type(Object object, String name) {
        for (Class<?> c = object == null ? null : object.getClass(); c != null; c = c.getSuperclass())
            if (c.getName().equals(name)) return true;
        return false;
    }

    static Object call(Object object, String method) throws ReflectiveOperationException {
        return object.getClass().getMethod(method).invoke(object);
    }

    static ViewGroup root(Activity activity) throws ReflectiveOperationException {
        return (ViewGroup) call(activity, "a0");
    }

    static boolean binding(Activity activity) throws ReflectiveOperationException {
        return Boolean.TRUE.equals(call(activity, "t3"));
    }

    static boolean replay(Activity activity, View source, Intent intent, Object item)
            throws ReflectiveOperationException {
        Class<?> itemType = Class.forName("com.android.launcher3.e0", false,
                activity.getClassLoader());
        Method method = activity.getClass().getMethod("j0", View.class, Intent.class, itemType);
        try {
            return Boolean.TRUE.equals(method.invoke(activity, source, intent, item));
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Error) throw (Error) cause;
            throw failure;
        }
    }

    static RectF artwork(View source) throws ReflectiveOperationException {
        int size = ((Number) call(source, "getIconSize")).intValue();
        int x = ((Number) call(source, "getIconOffsetX")).intValue();
        int y = ((Number) call(source, "getIconOffsetY")).intValue();
        return new RectF(x, y, x + size, y + size);
    }

    static Drawable drawable(View source) throws ReflectiveOperationException {
        return (Drawable) call(source, "getDrawableIcon");
    }

    /** Purpose: Express a child in overlay coordinates without changing its live transforms.
     * Invocation: Snapshot capture and launch-origin calculation.
     * Contract: The host must be an ancestor; scrolling is subtracted at each parent.
     * Verification: Matrix API/source review; transformed-device rendering remains unverified.
     */
    static Matrix toRoot(View child, ViewGroup root) {
        Matrix result = new Matrix();
        View view = child;
        while (view != root) {
            if (!(view.getParent() instanceof View))
                throw new IllegalArgumentException("Detached transition source");
            View parent = (View) view.getParent();
            Matrix step = new Matrix(view.getMatrix());
            step.postTranslate(view.getLeft() - parent.getScrollX(),
                    view.getTop() - parent.getScrollY());
            result.postConcat(step);
            view = parent;
        }
        return result;
    }

    static RectF bounds(View child, ViewGroup root) {
        RectF rect = new RectF(0, 0, child.getWidth(), child.getHeight());
        toRoot(child, root).mapRect(rect);
        return rect;
    }

    static boolean visible(View view) {
        if (!view.isShown() || !view.isLaidOut() || view.getWidth() <= 0 || view.getHeight() <= 0)
            return false;
        for (View v = view; v != null; v = v.getParent() instanceof View
                ? (View) v.getParent() : null)
            if (v.getAlpha() <= 0f) return false;
        return view.getGlobalVisibleRect(new Rect());
    }

    static View ancestor(View child, String name) {
        for (View v = child; v != null; v = v.getParent() instanceof View
                ? (View) v.getParent() : null)
            if (type(v, name)) return v;
        return null;
    }

    static void collect(View view, List<View> result) {
        if (!visible(view)) return;
        if (type(view, FOLDER_ICON) || type(view, BUBBLE)) {
            result.add(view);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), result);
        }
    }

    private static View findVisible(View view, String name) {
        if (!visible(view)) return null;
        if (type(view, name)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View found = findVisible(group.getChildAt(i), name);
                if (found != null) return found;
            }
        }
        return null;
    }

    static Scene scene(Activity activity, View source) throws ReflectiveOperationException {
        ViewGroup root = root(activity);
        if (root == null || !visible(root)) return null;
        View content = source == null ? null : ancestor(source, FOLDER);
        if (content == null) content = findVisible(root, FOLDER);
        if (content == null) {
            View search = (View) call(activity, "A2");
            View apps = (View) call(activity, "E2");
            if (search != null && visible(search)) content = search;
            else if (apps != null && visible(apps)) content = apps;
        }
        Scene scene = new Scene(root);
        if (content == null) {
            ViewGroup workspace = (ViewGroup) call(activity, "X2");
            int page = ((Number) call(workspace, "getCurrentPage")).intValue();
            if (page < 0 || page >= workspace.getChildCount()) return null;
            content = workspace.getChildAt(page);
            scene.home = true;
            View dock = (View) call(activity, "L2");
            View indicator = (View) call(activity, "R2");
            if (dock != null && visible(dock)) scene.strips.add(dock);
            if (indicator != null && visible(indicator)) scene.strips.add(indicator);
        }
        scene.content = content;
        collect(content, scene.icons);
        if (scene.home && source != null && ancestor(source, "com.android.launcher3.Hotseat") != null)
            scene.sourceInStrip = true;
        scene.populate();
        return scene;
    }

    /**
     * Purpose: Adapt Nova FloatingSurfaceView.Z / o9.i1 package/profile lookup to this launcher.
     * Invocation: Only with component/user supplied by the real gesture contract.
     * Contract: Visible hotseat precedes workspace; app type0 precedes widget type4. No local
     * last-launch guess or reverse-animation identity is retained. Stop at whole model views
     * (including folders) so preview children are not confused with visible standalone icons.
     * Verification: Nova raw matchers plus target e0 b/n/g contracts; device selection unverified.
     */
    static View gestureTarget(Scene scene, ComponentName component, UserHandle user)
            throws ReflectiveOperationException {
        List<View> candidates = new ArrayList<>();
        for (View strip : scene.strips) collectModelViews(strip, candidates);
        collectModelViews(scene.content, candidates);
        Class<?> model = Class.forName("com.android.launcher3.e0", false,
                scene.root.getContext().getClassLoader());
        for (int itemType : new int[] {0, 4}) {
            for (View candidate : candidates) {
                Object item = candidate.getTag();
                if (model.getField("b").getInt(item) != itemType
                        || !Objects.equals(user, model.getField("n").get(item))) continue;
                ComponentName target = (ComponentName) call(item, "g");
                if (target != null && component.getPackageName().equals(target.getPackageName())) {
                    scene.sourceInStrip = ancestor(candidate, "com.android.launcher3.Hotseat") != null;
                    return candidate;
                }
            }
        }
        return null;
    }

    private static void collectModelViews(View view, List<View> result) {
        if (view == null || !visible(view)) return;
        if (type(view.getTag(), "com.android.launcher3.e0")) {
            result.add(view);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                collectModelViews(group.getChildAt(i), result);
        }
    }

    /** Purpose: Nova's Picture records icon-only bounds for app cells and whole widget bounds.
     * Invocation: Gesture surface preparation. Contract: No framebuffer or foreign task capture.
     * Verification: FloatingIconView.c type branches and native target artwork accessors.
     */
    static RectF gestureArtwork(View source) throws ReflectiveOperationException {
        return type(source, BUBBLE) ? artwork(source)
                : new RectF(0, 0, source.getWidth(), source.getHeight());
    }

    static final class Item {
        final View view;
        final Matrix matrix;
        final RectF bounds;
        int column;
        int row;
        int ring;

        Item(View view, ViewGroup root) {
            this.view = view;
            matrix = toRoot(view, root);
            bounds = bounds(view, root);
        }
    }

    static final class Scene {
        final ViewGroup root;
        View content;
        boolean home;
        boolean sourceInStrip;
        final List<View> icons = new ArrayList<>();
        final List<View> strips = new ArrayList<>();
        final List<Item> items = new ArrayList<>();
        float anchorX;
        float anchorY;
        float cellHeight;
        int maxRing;

        Scene(ViewGroup root) { this.root = root; }

        /** Purpose: Reuse cell indices and dimensions; define an explicit extension for list surfaces.
         * Invocation: scene(), after the resulting target surface is selected.
         * Contract: Reference 4x4/4x5/4x6/5x4 centers are retained. Other layouts use middle cells;
         * list rows/columns are ranked from their actual visible centers, never root-pixel distance.
         * Verification: Source geometry contracts and timing tables; device geometry is unverified.
         * Visual: All items travel about one surface anchor; labels and folder artwork travel together.
         */
        void populate() throws ReflectiveOperationException {
            for (View icon : icons) items.add(new Item(icon, root));
            if (items.isEmpty()) {
                anchorX = root.getWidth() * .5f;
                anchorY = root.getHeight() * .5f;
                cellHeight = 1f;
                return;
            }
            View cell = ancestor(items.get(0).view, CELL);
            int columns;
            int rows;
            if (cell != null) {
                columns = ((Number) call(cell, "getCountX")).intValue();
                rows = ((Number) call(cell, "getCountY")).intValue();
                int cw = ((Number) call(cell, "getCellWidth")).intValue();
                int ch = ((Number) call(cell, "getCellHeight")).intValue();
                Item first = items.get(0);
                for (Item item : items) {
                    Object lp = item.view.getLayoutParams();
                    item.column = lp.getClass().getField("a").getInt(lp);
                    item.row = lp.getClass().getField("b").getInt(lp);
                }
                float[] vector = {0f, 0f, 0f, ch};
                toRoot(cell, root).mapPoints(vector);
                cellHeight = Math.max(1f, Math.abs(vector[3] - vector[1]));
                RectF art = type(first.view, BUBBLE) ? artwork(first.view)
                        : new RectF(0, 0, first.view.getWidth(), first.view.getWidth());
                float centerRow = MotionMath.centerRow(columns, rows);
                float[] artPoint = {art.centerX(), columns == 4 ? art.bottom : art.top};
                first.view.getMatrix().mapPoints(artPoint);
                View parent = (View) first.view.getParent();
                float px = first.view.getLeft() - parent.getScrollX() + artPoint[0]
                        + ((columns - 1) * .5f - first.column) * cw;
                float py = first.view.getTop() - parent.getScrollY() + artPoint[1]
                        + (centerRow - first.row) * ch;
                float[] anchor = {px, py};
                toRoot(parent, root).mapPoints(anchor);
                anchorX = anchor[0];
                anchorY = anchor[1];
            } else {
                List<Float> xs = new ArrayList<>();
                List<Float> ys = new ArrayList<>();
                for (Item item : items) {
                    addCoordinate(xs, item.bounds.centerX());
                    addCoordinate(ys, item.bounds.centerY());
                }
                Collections.sort(xs);
                Collections.sort(ys);
                columns = xs.size();
                rows = ys.size();
                for (Item item : items) {
                    item.column = closest(xs, item.bounds.centerX());
                    item.row = closest(ys, item.bounds.centerY());
                }
                anchorX = (xs.get((columns - 1) / 2) + xs.get(columns / 2)) * .5f;
                anchorY = ys.get(MotionMath.centerRow(columns, rows));
                cellHeight = items.get(0).bounds.height();
            }
            for (Item item : items) {
                item.ring = MotionMath.ring(item.column, item.row, columns, rows);
                maxRing = Math.max(maxRing, item.ring);
            }
        }

        private static void addCoordinate(List<Float> values, float value) {
            for (float old : values) if (Math.abs(old - value) < 2f) return;
            values.add(value);
        }

        private static int closest(List<Float> values, float value) {
            int best = 0;
            for (int i = 1; i < values.size(); i++)
                if (Math.abs(values.get(i) - value) < Math.abs(values.get(best) - value)) best = i;
            return best;
        }
    }
}
