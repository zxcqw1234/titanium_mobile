/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.kroll;

import org.appcelerator.kroll.common.TiMessenger;
import org.appcelerator.kroll.util.KrollAssetHelper;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;


public abstract class KrollRuntime implements Handler.Callback
{
	private static final String TAG = "KrollRuntime";
	private static final int MSG_DISPOSE = 100;
	private static final int MSG_RUN_MODULE = 101;
	private static final String PROPERTY_FILENAME = "filename";

	private static KrollRuntime instance;

	private KrollRuntimeThread thread;
	private long threadId;

	protected Handler handler;

	public static final int MSG_LAST_ID = MSG_RUN_MODULE + 100;

	public static final Object UNDEFINED = new Object() {
		public String toString()
		{
			return "undefined";
		}
	};

	public static final int DONT_INTERCEPT = Integer.MIN_VALUE + 1;
	public static final int DEFAULT_THREAD_STACK_SIZE = 16 * 1024;

	public static class KrollRuntimeThread extends Thread
	{
		private static final String TAG = "KrollRuntimeThread";

		private KrollRuntime runtime = null;

		public KrollRuntimeThread(KrollRuntime runtime, int stackSize)
		{
			super(null, null, TAG, stackSize);
			this.runtime = runtime;
		}

		public void run()
		{
			Looper looper;

			Looper.prepare();
			synchronized (this) {
				looper = Looper.myLooper();
				notifyAll();
			}

			// initialize the runtime instance
			runtime.threadId = looper.getThread().getId();
			runtime.handler = new Handler(looper, runtime);

			// initialize the TiMessenger instance for the runtime thread
			// NOTE: this must occur after threadId is set and before initRuntime() is called
			TiMessenger.getMessenger();

			runtime.initRuntime(); // initializer for the specific runtime implementation (V8, Rhino, etc)

			// start handling messages for this thread
			Looper.loop();
		}
	}

	public static void init(Context context, KrollRuntime runtime)
	{
		if (instance == null) {
			int stackSize = runtime.getThreadStackSize(context);
			runtime.thread = new KrollRuntimeThread(runtime, stackSize);
			runtime.thread.start();
			instance = runtime;
		}
		KrollAssetHelper.init(context);
	}

	public static KrollRuntime getInstance()
	{
		return instance;
	}

	public boolean isRuntimeThread()
	{
		return Thread.currentThread().getId() == threadId;
	}

	public long getThreadId()
	{
		return threadId;
	}

	public void dispose()
	{
		if (isRuntimeThread()) {
			doDispose();

		} else {
			handler.sendEmptyMessage(MSG_DISPOSE);
		}
	}

	public void runModule(String source, String filename)
	{
		if (isRuntimeThread()) {
			doRunModule(source, filename);

		} else {
			Message message = handler.obtainMessage(MSG_RUN_MODULE, source);
			message.getData().putString(PROPERTY_FILENAME, filename);
			message.sendToTarget();
		}
	}

	public int getThreadStackSize(Context context)
	{
		if (context instanceof KrollApplication) {
			KrollApplication app = (KrollApplication) context;
			return app.getThreadStackSize();
		}
		return DEFAULT_THREAD_STACK_SIZE;
	}

	public boolean handleMessage(Message msg)
	{
		switch (msg.what) {
			case MSG_DISPOSE:
				doDispose();
				return true;

			case MSG_RUN_MODULE:
				doRunModule((String) msg.obj, msg.getData().getString(PROPERTY_FILENAME));
				return true;
		}

		return false;
	}

	public abstract void initRuntime();
	public abstract void doDispose();
	public abstract void doRunModule(String source, String filename);
	public abstract void initObject(KrollProxySupport proxy);
}

