package com.fathzer.soft.jclop.swing;

import java.util.Collection;

import com.fathzer.soft.jclop.Account;
import com.fathzer.soft.jclop.Cancellable;
import com.fathzer.soft.jclop.Entry;

import net.astesana.ajlib.swing.worker.Worker;

final class RemoteFileListWorker extends Worker<Collection<Entry>, Void> implements Cancellable {
	private final Account account;

	RemoteFileListWorker(Account account) {
		this.account = account;
	}

	@Override
	protected Collection<Entry> doProcessing() throws Exception {
		return account.getService().getRemoteEntries(account, this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.astesana.ajlib.swing.worker.Worker#setPhase(java.lang.String, int)
	 */
	@Override
	public void setPhase(String phase, int phaseLength) {
		super.setPhase(phase, phaseLength);
	}

	@Override
	public void setCancelAction(Runnable action) {
	}
}