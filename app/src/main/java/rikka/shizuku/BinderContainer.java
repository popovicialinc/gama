package moe.shizuku.api;

import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Minimal stub of BinderContainer — vendored to avoid the shizuku-api library dependency.
 */
public class BinderContainer implements Parcelable {

    public IBinder binder;

    public BinderContainer(IBinder binder) {
        this.binder = binder;
    }

    protected BinderContainer(Parcel in) {
        binder = in.readStrongBinder();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStrongBinder(binder);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<BinderContainer> CREATOR = new Creator<BinderContainer>() {
        @Override
        public BinderContainer createFromParcel(Parcel in) {
            return new BinderContainer(in);
        }

        @Override
        public BinderContainer[] newArray(int size) {
            return new BinderContainer[size];
        }
    };
}
