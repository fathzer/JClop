package com.fathzer.soft.jclop.swing;

import javax.swing.AbstractAction;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.Window;

import javax.swing.JButton;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.io.IOException;
import java.net.URI;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import net.astesana.ajlib.swing.widget.ComboBox;
import net.astesana.ajlib.swing.Utils;
import net.astesana.ajlib.swing.dialog.urichooser.AbstractURIChooserPanel;
import net.astesana.ajlib.swing.dialog.urichooser.MultipleURIChooserDialog;
import net.astesana.ajlib.swing.table.JTableListener;
import net.astesana.ajlib.swing.widget.TextWidget;
import net.astesana.ajlib.swing.worker.WorkInProgressFrame;

import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.basic.BasicComboBoxRenderer;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JProgressBar;
import javax.swing.JScrollPane;

import com.fathzer.soft.jclop.Account;
import com.fathzer.soft.jclop.Entry;
import com.fathzer.soft.jclop.Service;

@SuppressWarnings("serial")
public abstract class URIChooser extends JPanel implements AbstractURIChooserPanel {
	private JPanel centerPanel;
	private JTable fileList;
	private JPanel filePanel;
	private JLabel lblNewLabel;
	private TextWidget fileNameField;
	
	private JLabel lblAccount;
	private JPanel northPanel;
	private JButton refreshButton;
	private JProgressBar progressBar;
	private FilesTableModel filesModel;
	private JScrollPane scrollPane;
	
	private JPanel panel;
	private ComboBox accountsCombo;
	private JButton btnNewAccount;
	private JButton deleteButton;
	private Service service;
	private String initedAccountId;
	
	private URI selectedURI;
	
	public URIChooser(Service service) {
		this.service = service;
		this.initedAccountId = null;
		this.filesModel = new FilesTableModel();
		setLayout(new BorderLayout(0, 0));
		add(getNorthPanel(), BorderLayout.NORTH);
		add(getCenterPanel(), BorderLayout.CENTER);
	}
	
	public void setDialogType(boolean save) {
		this.getFilePanel().setVisible(save);
	}

	public URI showOpenDialog(Component parent, String title) {
		setDialogType(false);
		return showDialog(parent, title);
	}
	
	public URI showSaveDialog(Component parent, String title) {
		setDialogType(true);
		return showDialog(parent, title);
	}
	
	public URI showDialog(Component parent, String title) {
		Window owner = Utils.getOwnerWindow(parent);
		MultipleURIChooserDialog dialog = new MultipleURIChooserDialog(owner, title, new AbstractURIChooserPanel[]{this});
		dialog.setSaveDialog(this.getFilePanel().isVisible());
		return dialog.showDialog();
	}

	public void refresh(boolean force) {
		Account account = (Account) getAccountsCombo().getSelectedItem();
		if (force || ((account!=null) && !account.getId().equals(initedAccountId))) {
			initedAccountId = account.getId();
			RemoteFileListWorker worker = new RemoteFileListWorker(account);
			worker.setPhase(getRemoteConnectingWording(), -1); //$NON-NLS-1$
			final Window owner = Utils.getOwnerWindow(this);
			WorkInProgressFrame frame = new WorkInProgressFrame(owner, Messages.getString("GenericWait.title"), ModalityType.APPLICATION_MODAL, worker); //$NON-NLS-1$
			frame.setSize(300, frame.getSize().height);
			Utils.centerWindow(frame, owner);
			frame.setVisible(true); //$NON-NLS-1$
			try {
				Collection<Entry> entries = worker.get();
				fillTable(entries);
				getFileNameField().setEditable(true);
				setQuota(account);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			} catch (ExecutionException e) {
				//FIXME
	//			if (e.getCause() instanceof DropboxIOException) {
	//				JOptionPane.showMessageDialog(owner, LocalizationData.get("dropbox.Chooser.error.connectionFailed"), LocalizationData.get("Generic.warning"), JOptionPane.WARNING_MESSAGE); //$NON-NLS-1$ //$NON-NLS-2$
	//			} else if (e.getCause() instanceof DropboxUnlinkedException) {
	//				System.err.println ("Not linked !!!");
	//				throw new RuntimeException(e);
	//			} else {
					throw new RuntimeException(e);
	//			}
			} catch (CancellationException e) {
				// The task was cancelled
				setQuota(null);
			}
		}
	}

	private void setQuota(Account account) {
		if ((account!=null) && (account.getQuota()>0)) {
			long percentUsed = 100*(account.getUsed()) / account.getQuota(); 
			getProgressBar().setValue((int)percentUsed);
			double remaining = account.getQuota()-account.getUsed();
			String unit = ""; //$NON-NLS-1$
			if (remaining>1024) {
				unit = ""; //$NON-NLS-1$
				remaining = remaining/1024;
				if (remaining>1024) {
					unit = ""; //$NON-NLS-1$
					remaining = remaining/1024;
					if (remaining>1024) {
						unit = ""; //$NON-NLS-1$
						remaining = remaining/1024;
					}
				}
			}
			getProgressBar().setString(MessageFormat.format("", new DecimalFormat("0.0").format(remaining), unit));  //$NON-NLS-2$
			getProgressBar().setVisible(true);
		} else {
			getProgressBar().setVisible(false);
		}
	}
	
	private JPanel getCenterPanel() {
		if (centerPanel == null) {
			centerPanel = new JPanel();
			centerPanel.setLayout(new BorderLayout(0, 0));
			centerPanel.add(getScrollPane(), BorderLayout.CENTER);
			centerPanel.add(getFilePanel(), BorderLayout.SOUTH);
		}
		return centerPanel;
	}
	private JTable getFileList() {
		if (fileList == null) {
			fileList = new JTable(filesModel);
			fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			fileList.addMouseListener(new JTableListener(null, new AbstractAction() {
				@Override
				public void actionPerformed(ActionEvent e) {
					URIChooser.this.firePropertyChange(URI_APPROVED_PROPERTY, false, true);
				}
			}));
			fileList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
				@Override
				public void valueChanged(ListSelectionEvent e) {
					if (!e.getValueIsAdjusting()) {
						if (getFileList().getSelectedRow()!=-1) {
							getFileNameField().setText((String) filesModel.getValueAt(getFileList().getSelectedRow(), 0));
						}
					}
				}
			});
		}
		return fileList;
	}
	private JPanel getFilePanel() {
		if (filePanel == null) {
			filePanel = new JPanel();
			filePanel.setLayout(new BorderLayout(0, 0));
			filePanel.add(getLblNewLabel(), BorderLayout.WEST);
			filePanel.add(getFileNameField(), BorderLayout.CENTER);
		}
		return filePanel;
	}
	private JLabel getLblNewLabel() {
		if (lblNewLabel == null) {
			lblNewLabel = new JLabel(""); 
		}
		return lblNewLabel;
	}
	private TextWidget getFileNameField() {
		if (fileNameField == null) {
			fileNameField = new TextWidget();
			fileNameField.setEditable(false);
			fileNameField.addPropertyChangeListener(TextWidget.TEXT_PROPERTY, new PropertyChangeListener() {	
				@Override
				public void propertyChange(PropertyChangeEvent evt) {
					int pos = fileNameField.getCaretPosition();
					int index = -1;
					for (int rowIndex=0;rowIndex<filesModel.getRowCount();rowIndex++) {
						if (filesModel.getValueAt(rowIndex, 0).equals(evt.getNewValue())) {
							index = rowIndex;
							break;
						}
					}
					ListSelectionModel selectionModel = getFileList().getSelectionModel();
					if (index<0) {
						selectionModel.clearSelection();
					} else {
						selectionModel.setSelectionInterval(index, index);
					}
					URI old = selectedURI;
					String name = getFileNameField().getText();
					Account account = (Account) getAccountsCombo().getSelectedItem();
					if (account!=null) {
						Entry entry = new Entry(account, name); 
						selectedURI = ((account==null) || (name.length()==0))?null:getService().getURI(entry);
						firePropertyChange(SELECTED_URI_PROPERTY, old, getSelectedURI());
						pos = Math.min(pos, fileNameField.getText().length());
						fileNameField.setCaretPosition(pos);
					}
				}
			});
		}
		return fileNameField;
	}
	
	private JLabel getLblAccount() {
		if (lblAccount == null) {
			lblAccount = new JLabel(MessageFormat.format("", ""));  //$NON-NLS-2$
		}
		return lblAccount;
	}

	private JPanel getNorthPanel() {
		if (northPanel == null) {
			northPanel = new JPanel();
			GridBagLayout gbl_northPanel = new GridBagLayout();
			northPanel.setLayout(gbl_northPanel);
			GridBagConstraints gbc_panel = new GridBagConstraints();
			gbc_panel.weightx = 1.0;
			gbc_panel.fill = GridBagConstraints.BOTH;
			gbc_panel.insets = new Insets(0, 0, 0, 5);
			gbc_panel.gridx = 0;
			gbc_panel.gridy = 0;
			northPanel.add(getPanel(), gbc_panel);
			GridBagConstraints gbc_refreshButton = new GridBagConstraints();
			gbc_refreshButton.gridheight = 0;
			gbc_refreshButton.gridx = 1;
			gbc_refreshButton.gridy = 0;
			northPanel.add(getRefreshButton(), gbc_refreshButton);
			GridBagConstraints gbc_progressBar = new GridBagConstraints();
			gbc_progressBar.fill = GridBagConstraints.HORIZONTAL;
			gbc_progressBar.insets = new Insets(5, 0, 5, 5);
			gbc_progressBar.gridx = 0;
			gbc_progressBar.gridy = 1;
			northPanel.add(getProgressBar(), gbc_progressBar);
		}
		return northPanel;
	}
	private JButton getRefreshButton() {
		if (refreshButton == null) {
			refreshButton = new JButton(IconPack.PACK.getSynchronize());
			refreshButton.setToolTipText("Raffraichissements !!!"); 
			refreshButton.setEnabled(getAccountsCombo().getItemCount()!=0);
			refreshButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					refresh(true);
				}
			});
		}
		return refreshButton;
	}
	private JProgressBar getProgressBar() {
		if (progressBar == null) {
			progressBar = new JProgressBar();
			progressBar.setStringPainted(true);
			progressBar.setVisible(false);
		}
		return progressBar;
	}

	private void fillTable(Collection<Entry> entries) {
		filesModel.clear();
		for (Entry entry : entries) {
			Entry filtered = filter(entry);
			if (filtered!=null) filesModel.add(entry);
		}
	}

	/** Filters an entry.
	 * <br>By default, this method returns the entry path.
	 * @param entry The entry available in the current Dropbox folder
	 * @return The entry that will be displayed in the files list, or null to ignore this entry
	 */
	protected Entry filter(Entry entry) {
		return entry;
	}
	
	private JScrollPane getScrollPane() {
		if (scrollPane == null) {
			scrollPane = new JScrollPane();
			scrollPane.setViewportView(getFileList());
			// Do not diplay column names
			getFileList().setTableHeader(null);
			scrollPane.setColumnHeaderView(null);
		}
		return scrollPane;
	}

	public URI getSelectedURI() {
		return selectedURI;
	}
	
	public void setSelectedURI(URI uri) {
		if (uri==null) {
			getFileNameField().setText(""); //$NON-NLS-1$
		} else {
			//FIXME
			Entry entry = service.getEntry(uri);
			System.out.println (entry+" is selected");
//			FileId id = FileId.fromURI(uri);
//			if (!getInfo().getAccount().displayName.equals(id.getAccount())) throw new IllegalArgumentException("invalid account"); //$NON-NLS-1$
//			getFileNameField().setText(uri.getPath().substring(1));
		}
		selectedURI = uri;
	}
	
	private JPanel getPanel() {
		if (panel == null) {
			panel = new JPanel();
			GridBagLayout gbl_panel = new GridBagLayout();
			panel.setLayout(gbl_panel);
			GridBagConstraints gbc_lblAccount = new GridBagConstraints();
			gbc_lblAccount.fill = GridBagConstraints.BOTH;
			gbc_lblAccount.insets = new Insets(0, 0, 0, 5);
			gbc_lblAccount.anchor = GridBagConstraints.EAST;
			gbc_lblAccount.gridx = 0;
			gbc_lblAccount.gridy = 0;
			panel.add(getLblAccount(), gbc_lblAccount);
			GridBagConstraints gbc_accountsCombo = new GridBagConstraints();
			gbc_accountsCombo.weightx = 1.0;
			gbc_accountsCombo.fill = GridBagConstraints.BOTH;
			gbc_accountsCombo.gridx = 1;
			gbc_accountsCombo.gridy = 0;
			panel.add(getAccountsCombo(), gbc_accountsCombo);
			GridBagConstraints gbc_btnNewAccount = new GridBagConstraints();
			gbc_btnNewAccount.gridx = 2;
			gbc_btnNewAccount.gridy = 0;
			panel.add(getBtnNewAccount(), gbc_btnNewAccount);
			GridBagConstraints gbc_deleteButton = new GridBagConstraints();
			gbc_deleteButton.insets = new Insets(0, 0, 0, 5);
			gbc_deleteButton.gridx = 3;
			gbc_deleteButton.gridy = 0;
			panel.add(getDeleteButton(), gbc_deleteButton);
		}
		return panel;
	}
	private ComboBox getAccountsCombo() {
		if (accountsCombo == null) {
			accountsCombo = new ComboBox();
			accountsCombo.setRenderer(new BasicComboBoxRenderer(){
				@Override
				public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
					if (value!=null) value = ((Account)value).getDisplayName();
					return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				}
			});
			Collection<Account> accounts = getService().getAccounts();
			for (Account account : accounts) {
				accountsCombo.addItem(account);
			}
			accountsCombo.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					boolean oneIsSelected = getAccountsCombo().getSelectedIndex()>=0;
					getDeleteButton().setEnabled(oneIsSelected);
					getRefreshButton().setEnabled(oneIsSelected);
					refresh(false);
				}
			});
		}
		return accountsCombo;
	}
	private JButton getBtnNewAccount() {
		if (btnNewAccount == null) {
			btnNewAccount = new JButton(IconPack.PACK.getNewAccount());
			int height = getAccountsCombo().getPreferredSize().height;
			btnNewAccount.setPreferredSize(new Dimension(height, height));
			btnNewAccount.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent event) {
					Account account = createNewAccount();
					if (account!=null) {
						// Test if account is already there
						for (int i = 0; i < getAccountsCombo().getItemCount(); i++) {
							if (((Account)getAccountsCombo().getItemAt(i)).getDisplayName().equals(account.getDisplayName())) {
								getAccountsCombo().setSelectedIndex(i);
								return;
							}
						}
						// Save the account data to disk
						try {
							account.serialize();
						} catch (IOException e) {
							//FIXME Alert the user something went wrong
						}
						getAccountsCombo().addItem(account);
					}
				}
			});
		}
		return btnNewAccount;
	}
	private JButton getDeleteButton() {
		if (deleteButton == null) {
			deleteButton = new JButton(IconPack.PACK.getDeleteAccount());
			deleteButton.setEnabled(getAccountsCombo().getItemCount()!=0);
			deleteButton.setToolTipText("Deletes the current account");
			int height = getAccountsCombo().getPreferredSize().height;
			deleteButton.setPreferredSize(new Dimension(height, height));
			deleteButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					boolean confirm = JOptionPane.showOptionDialog(Utils.getOwnerWindow(deleteButton), "<html>Are you sure you want to delete this account ?<br><br>It will only delete the copy made on your computer, not the remote account.</html>", "Delete account",
							JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, new String[]{Messages.getString("GenericButton.ok"),Messages.getString("GenericButton.cancel")},1)==0; //$NON-NLS-1$ //$NON-NLS-2$
					if (confirm) {
						Account account = (Account) getAccountsCombo().getSelectedItem();
						getAccountsCombo().removeItemAt(getAccountsCombo().getSelectedIndex());
						account.delete();
					}
				}
			});
		}
		return deleteButton;
	}

	protected abstract Account createNewAccount();

	public Service getService() {
		return service;
	}

	protected String getRemoteConnectingWording() {
		return "Connecting to remote host ..."; //LOCAL
	}

	/* (non-Javadoc)
	 * @see net.astesana.ajlib.swing.dialog.urichooser.AbstractURIChooserPanel#getSchemes()
	 */
	@Override
	public String getScheme() {
		return service.getScheme();
	}

	/* (non-Javadoc)
	 * @see net.astesana.ajlib.swing.dialog.urichooser.AbstractURIChooserPanel#setUp()
	 */
	@Override
	public void setUp() {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				refresh(false);
			}
		});
	}

	/* (non-Javadoc)
	 * @see net.astesana.ajlib.swing.dialog.urichooser.AbstractURIChooserPanel#exist(java.net.URI)
	 */
	@Override
	public boolean isSelectedExist() {
		// If the selectedFile exists, it is selected in the file list as there's a listener on the file name field
		return getFileList().getSelectedRow()>=0;
	}
	
	public static void showError(Window owner, String message) {
		JOptionPane.showMessageDialog(owner, message, Messages.getString("Error.title"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$

	}
}