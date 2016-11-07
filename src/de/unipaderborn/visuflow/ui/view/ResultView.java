package de.unipaderborn.visuflow.ui.view;

import java.util.Hashtable;
import java.util.List;

import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import de.unipaderborn.visuflow.model.DataModel;
import de.unipaderborn.visuflow.model.VFUnit;
import de.unipaderborn.visuflow.ui.view.filter.ResultViewFilter;
import de.unipaderborn.visuflow.util.ServiceUtil;

public class ResultView extends ViewPart implements EventHandler {

	private TableViewer viewer;
	private ResultViewFilter filter;
	private List<VFUnit> units;

	@Override
	public void createPartControl(Composite parent) {
		GridLayout layout = new GridLayout(2, false);
		parent.setLayout(layout);
		Label searchLabel = new Label(parent, SWT.NONE);
		searchLabel.setText("Search: ");
		final Text searchText = new Text(parent, SWT.BORDER | SWT.SEARCH);
		searchText.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL));
		this.units = ServiceUtil.getService(DataModel.class).getSelectedMethodUnits();
		createViewer(parent);

		searchText.addKeyListener(new KeyAdapter() {
			public void keyReleased(KeyEvent ke) {
				filter.setSearchText(searchText.getText());
				viewer.refresh();
			}

		});
		filter = new ResultViewFilter();
		viewer.addFilter(filter);
	}

	@Override
	public void setFocus() {
		viewer.getControl().setFocus();
	}

	private void createViewer(Composite parent) {
		viewer = new TableViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER | SWT.CHECK | SWT.READ_ONLY);
		createColumns(parent, viewer);
		final Table table = viewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);

		viewer.setContentProvider(new ArrayContentProvider());
		viewer.setInput(this.units);
		getSite().setSelectionProvider(viewer);

		GridData gridData = new GridData();
		gridData.verticalAlignment = GridData.FILL;
		gridData.horizontalSpan = 2;
		gridData.grabExcessHorizontalSpace = true;
		gridData.grabExcessVerticalSpace = true;
		gridData.horizontalAlignment = GridData.FILL;
		viewer.getControl().setLayoutData(gridData);

		Hashtable<String, String> properties = new Hashtable<String, String>();
		properties.put(EventConstants.EVENT_TOPIC, DataModel.EA_TOPIC_DATA_SELECTION);
		ServiceUtil.registerService(EventHandler.class, this, properties);
	}

	private void createColumns(final Composite parent, final TableViewer viewer) {
		String[] titles = { "Unit", "Unit Type", "In-Set", "Out-Set"};
		int[] bounds = { 100, 100, 100, 100 };
		//Unit
		TableViewerColumn col = createTableViewerColumn(titles[0], bounds[0], 0);
		col.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				VFUnit unit = (VFUnit) element;
				return unit.getUnit().toString();
			}
		});
		//Unit Type
		col = createTableViewerColumn(titles[1], bounds[1], 1);
		col.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				VFUnit unit = (VFUnit) element;
				return unit.getUnit().getClass().getName();
			}
		});
		//In-Set
		col = createTableViewerColumn(titles[2], bounds[1], 1);
		col.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return "InSet";
			}
		});
		//Out-Set
		col = createTableViewerColumn(titles[3], bounds[1], 1);
		col.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return "OutSet";
			}
		});
	}

	private TableViewerColumn createTableViewerColumn(String title, int bound, final int colNumber) {
		final TableViewerColumn viewerColumn = new TableViewerColumn(viewer,
				SWT.NONE);
		final TableColumn column = viewerColumn.getColumn();
		column.setText(title);
		column.setWidth(bound);
		column.setResizable(true);
		column.setMoveable(true);
		return viewerColumn;
	}


	@SuppressWarnings("unchecked")
	@Override
	public void handleEvent(Event event) {
		viewer.getTable().getDisplay().asyncExec(new Runnable() {
			
			@Override
			public void run() {
				viewer.setInput((List<VFUnit>) event.getProperty("selectedMethodUnits"));
			}
		});
		
	}
}
