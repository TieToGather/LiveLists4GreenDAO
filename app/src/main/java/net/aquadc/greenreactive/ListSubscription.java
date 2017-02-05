package net.aquadc.greenreactive;

import android.os.Handler;
import android.support.annotation.Nullable;

import org.greenrobot.greendao.query.LazyList;
import org.greenrobot.greendao.query.Query;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Created by miha on 04.02.17
 */

/*pkg*/ final class ListSubscription<T extends LiveDataLayer.WithId> {

//    private static final long[] EMPTY_LONGS = {};
//    private static final String[] ID = {"_id"};

    private final Handler handler;
    private final Query<T> query;
    /*pkg*/ final LiveDataLayer.BaseListSubscriber<T> subscriber;

    private LazyList<T> list;
    private long[] ids;

    /*pkg*/ ListSubscription(Handler handler, Query<T> query, LiveDataLayer.BaseListSubscriber<T> subscriber) {
        this.handler = handler;
        this.query = query;
        this.subscriber = subscriber;

        LazyList<T> list = query.listLazy();
        this.list = list;
        long[] ids = loadIds(query);
        this.ids = ids;

        List<T> uList = Collections.unmodifiableList(list);
        long[] uIds = ids.clone(); // fixme don't clone in some cases
        Set<Long> changes = Collections.emptySet();
        if (subscriber instanceof LiveDataLayer.ListSubscriberWithPayload) {
            LiveDataLayer.ListSubscriberWithPayload sub =
                    (LiveDataLayer.ListSubscriberWithPayload) subscriber;
            sub.onStructuralChange(uList, uIds, changes, sub.calculatePayload(uList, uIds, changes));
        } else if (subscriber instanceof LiveDataLayer.ListSubscriber) {
            ((LiveDataLayer.ListSubscriber<T>) subscriber).onStructuralChange(
                    uList, uIds, changes);
        }
    }

    /*pkg*/ void dispatchUpdate(T newT) {
        if (containId(newT.getId())) {
            dispatchNonStructuralChange(newT.getId());
        }
    }

    private boolean containId(Long pk) {
        long id = pk/*.longValue()*/;
        for (long l : ids) {
            if (l == id) {
                return true;
            }
        }
        return false;
    }

    /*pkg*/ void dispatchStructuralChange(final Long idOfInsertedOrRemoved, int delta) {
        final LazyList<T> oldList = list;
        final LazyList<T> newList = query.listLazy();
        final long[] newIds = loadIds(query);

        int newSize = newList.size();
        int oldSize = oldList.size();

        if (newSize == oldSize) {
            newList.close();
            return; // change not affected this query
        }

        list = newList;
        ids = newIds;

        final List<T> uList = Collections.unmodifiableList(newList);
        final long[] uIds = newIds.clone(); // fixme
        final Set<Long> changed = Collections.singleton(idOfInsertedOrRemoved);

        LiveDataLayer.BaseListSubscriber<T> sub = subscriber;
        final Object payload = sub instanceof LiveDataLayer.ListSubscriberWithPayload
                ? ((LiveDataLayer.ListSubscriberWithPayload) sub).calculatePayload(uList, newIds, changed)
                : null;
        handler.post(new Runnable() {
            @Override
            public void run() {
                LiveDataLayer.BaseListSubscriber<T> sub = subscriber;
                if (sub instanceof LiveDataLayer.ListSubscriberWithPayload) {
                    ((LiveDataLayer.ListSubscriberWithPayload) sub).onStructuralChange(uList, uIds, changed, payload);
                } else if (sub instanceof LiveDataLayer.ListSubscriber) {
                    ((LiveDataLayer.ListSubscriber) sub).onStructuralChange(uList, uIds, changed);
                }
                oldList.close();
            }
        });
    }

    private void dispatchNonStructuralChange(@Nullable final Long idOfChanged) {
        final LazyList<T> list = this.list;
        handler.post(new Runnable() {
            @Override
            public void run() {
                subscriber.onChange(list, idOfChanged == null
                        ? Collections.<Long>emptySet() : Collections.singleton(idOfChanged));
            }
        });
    }

    /*pkg*/ void dispose() {
        handler.removeCallbacksAndMessages(null);
        list.close();
    }

    private static long[] loadIds(Query<? extends LiveDataLayer.WithId> query) { // fixme absolutely awful stub impl
        LazyList<? extends LiveDataLayer.WithId> list = query.listLazyUncached();
        long[] ids = new long[list.size()];
        for (int i = 0; i < list.size(); i++) {
            ids[i] = list.get(i).getId();
        }
        return ids;

        /*Cursor cur = db.query(table, ID, null, null, null, null, null);
        if (!cur.moveToFirst()) {
            cur.close();
            return EMPTY_LONGS;
        }

        long[] array = new long[cur.getCount()];
        int pos = 0;
        do {
            array[pos] = cur.getLong(0);
            pos++;
        } while (cur.moveToNext());
        cur.close();
        return array;*/
    }
}
