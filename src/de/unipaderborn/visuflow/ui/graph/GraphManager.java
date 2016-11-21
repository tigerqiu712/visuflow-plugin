package de.unipaderborn.visuflow.ui.graph;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import javax.swing.JApplet;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.JToolTip;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.ui.graphicGraph.GraphicElement;
import org.graphstream.ui.layout.Layout;
import org.graphstream.ui.layout.springbox.implementations.SpringBox;
import org.graphstream.ui.swingViewer.ViewPanel;
import org.graphstream.ui.view.Viewer;
import org.graphstream.ui.view.ViewerListener;
import org.graphstream.ui.view.ViewerPipe;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import de.unipaderborn.visuflow.model.DataModel;
import de.unipaderborn.visuflow.model.VFClass;
import de.unipaderborn.visuflow.model.VFEdge;
import de.unipaderborn.visuflow.model.VFMethod;
import de.unipaderborn.visuflow.model.VFMethodEdge;
import de.unipaderborn.visuflow.model.VFNode;
import de.unipaderborn.visuflow.model.graph.ControlFlowGraph;
import de.unipaderborn.visuflow.model.graph.ICFGStructure;
import de.unipaderborn.visuflow.util.ServiceUtil;

public class GraphManager implements Runnable, ViewerListener {

	Graph graph;
	String styleSheet;
	private Viewer viewer;
	private ViewPanel view;
	List<VFClass> analysisData;

	Container panel;
	JApplet applet;
	JButton zoomInButton, zoomOutButton, viewCenterButton, toggleLayout, showICFGButton;
	JToolBar settingsBar;
	JScrollPane scrollbar;

	double zoomInDelta, zoomOutDelta, maxZoomPercent, minZoomPercent;

	boolean autoLayoutEnabled = false;

	Layout graphLayout = new SpringBox();

	private JToolTip tip;

	public GraphManager(String graphName, String styleSheet)
	{
		System.setProperty("sun.awt.noerasebackground", "true");
		System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");
		this.zoomInDelta = .2;
		this.zoomOutDelta = .2;
		this.maxZoomPercent = 1.0;
		this.minZoomPercent = 3.0;
		this.styleSheet = styleSheet;
		createGraph(graphName);
		createUI();
	}

	public Container getApplet() {
		return applet.getRootPane();
	}

	void createGraph(String graphName)
	{
		graph = new MultiGraph(graphName);
		graph.addAttribute("ui.stylesheet", styleSheet);
		graph.setStrict(true);
		graph.setAutoCreate(true);
		graph.addAttribute("ui.quality");
		graph.addAttribute("ui.antialias");

		viewer = new Viewer(graph, Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
		viewer.setCloseFramePolicy(Viewer.CloseFramePolicy.CLOSE_VIEWER);

		view = viewer.addDefaultView(false);
		view.getCamera().setAutoFitView(true);
	}

//	private void createDefaultNodes()
//	{
//		Node showICFGNode = graph.addNode("showICFG");
//		showICFGNode.setAttribute("ui.label", "Show ICFG");
//		showICFGNode.setAttribute("nodeData.label", "Show ICFG");
//		showICFGNode.addAttribute("xyz", 0.0, 0.0, 0.0);
//		showICFGNode.addAttribute("layout.frozen");
//	}

	private void reintializeGraph() throws Exception
	{
		if(graph != null)
		{
			graph.clear();
			graph.addAttribute("ui.stylesheet", styleSheet);
			graph.setStrict(true);
			graph.setAutoCreate(true);
			graph.addAttribute("ui.quality");
			graph.addAttribute("ui.antialias");
//			createDefaultNodes();
		}
		else
			throw new Exception("Graph is null");
	}

	private void createUI() {
		createZoomControls();
		createShowICFGButton();
		createViewListeners();
		createToggleLayoutButton();
		createSettingsBar();
		createPanel();
		createAppletContainer();
	}

	private void createShowICFGButton() {
		showICFGButton = new JButton("Show ICFG");
		showICFGButton.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				renderICFG(ServiceUtil.getService(DataModel.class).getIcfg());
			}
		});
	}

	private void createAppletContainer() {
		applet = new JApplet();

		scrollbar = new JScrollPane(panel, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS );
		view.setAutoscrolls(true);
		applet.add(scrollbar);
	}

	private void createSettingsBar() {
		settingsBar = new JToolBar("ControlsBar", JToolBar.HORIZONTAL);

		settingsBar.add(zoomInButton);
		settingsBar.add(zoomOutButton);
		settingsBar.add(showICFGButton);
		settingsBar.add(viewCenterButton);
		settingsBar.add(toggleLayout);
	}

	private void createPanel() {
		panel = new JFrame().getContentPane();
		panel.add(view);
		panel.add(settingsBar, BorderLayout.PAGE_END);
	}

	private void createViewListeners() {
		view.addMouseWheelListener(new MouseWheelListener() {

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				int rotationDirection = e.getWheelRotation();
				if(rotationDirection > 0)
					zoomIn();
				else
					zoomOut();
			}
		});

		view.addMouseMotionListener(new MouseMotionListener() {

			@Override
			public void mouseMoved(MouseEvent event) {

				GraphicElement curElement = view.findNodeOrSpriteAt(event.getX(), event.getY());

				if(curElement == null && tip != null) {
					tip.setVisible(false);
					setTip(null);
					view.repaint();
				}

				if(curElement != null && tip == null) {
					Node node=graph.getNode(curElement.getId());
					String result = "<html><table>";
					int maxToolTipLength=0;
					int height=0;
					for(String key:node.getEachAttributeKey()) {
						if(key.startsWith("nodeData")){
							height++;
							Object value = node.getAttribute(key);
							String tempVal=key.substring(key.lastIndexOf(".")+1)+" : "+value.toString();
							if(tempVal.length()>maxToolTipLength){
								maxToolTipLength=tempVal.length();
							}

							result+="<tr><td>"+key.substring(key.lastIndexOf(".")+1)+"</td>"+"<td>"+value.toString()+"</td></tr>";
						}
					}
					result+="</table></html>";
					tip = new JToolTip();
					String tipText = result;
					tip.setTipText(tipText);
					tip.setBounds(event.getX() - tipText.length()*3 + 1, event.getY(), maxToolTipLength*6+3,height*30 );
					setTip(tip);
					tip.setVisible(true);

					if(tipText.length() > 10) {
						tip.setLocation(event.getX()-15, event.getY());
					}

					view.add(tip);
					tip.repaint();
				}
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				// TODO Auto-generated method stub
				int x = e.getX();
				int y = e.getY();
				System.out.println("x and y from event " + x + "  " + y);
				System.out.println("x and y from camera " + view.getCamera().getViewCenter().x + "  " + view.getCamera().getViewCenter().y);
				System.out.println("x and y from default view " + viewer.getDefaultView().getCamera().getViewCenter().x + "  " + viewer.getDefaultView().getCamera().getViewCenter().y);
				view.getCamera().setBounds(x-10, y-10, x+10, y+10, 0.0, 0.0);
//				view.getCamera().setGraphViewport(x-10.0, y-10.0, x+10.0, y+10.0);
				
				/*if(e.getButton() == 0)
				{
					Point dest = e.getPoint();
					System.out.println("dragged with button");
					System.out.println(dest);

					Point3 currViewCenter = view.getCamera().getViewCenter();

					for(int i=0; i<e.getClickCount(); i++)
					{
						view.getCamera().setViewCenter(currViewCenter.x+.2, currViewCenter.y+.2, 0);
						//						try {
						//							Thread.sleep(1000);
						//						} catch (InterruptedException e1) {
						//							// TODO Auto-generated catch block
						//							e1.printStackTrace();
						//						}
					}
				}*/
			}
		});

		view.addMouseListener(new MouseListener() {

			@Override
			public void mouseReleased(MouseEvent e) {
				//noop
			}

			@Override
			public void mousePressed(MouseEvent e) {
				//noop
			}

			@Override
			public void mouseExited(MouseEvent e) {
				//noop
			}

			@Override
			public void mouseEntered(MouseEvent e) {
				//noop
			}

			@Override
			public void mouseClicked(MouseEvent e) {
				if(e.getButton() == MouseEvent.BUTTON3)
				{
					GraphicElement curElement = view.findNodeOrSpriteAt(e.getX(), e.getY());
					if(curElement == null)
						return;
					Node curr = graph.getNode(curElement.getId());
					Object node = curr.getAttribute("nodeMethod");
					if(node instanceof VFMethod)
					{
						VFMethod currentMethod = (VFMethod) node;
						DataModel dataModel = ServiceUtil.getService(DataModel.class);
						VFMethod selectedMethod = dataModel.getVFMethodByName(currentMethod.getSootMethod());
						try {
							if(selectedMethod.getControlFlowGraph() == null)
								throw new Exception("CFG Null Exception");
							else
							{
								renderMethodCFG(dataModel.getVFMethodByName(currentMethod.getSootMethod()).getControlFlowGraph());
								dataModel.setSelectedMethod(selectedMethod);
							}
						} catch (Exception e1) {
							e1.printStackTrace();
						}
					}
				}
			}
		});
	}

	private void zoomIn()
	{
		double viewPercent = view.getCamera().getViewPercent();
		if(viewPercent > maxZoomPercent)
			view.getCamera().setViewPercent(viewPercent - zoomInDelta);
	}

	private void zoomOut()
	{
		double viewPercent = view.getCamera().getViewPercent();
		if(viewPercent < minZoomPercent)
			view.getCamera().setViewPercent(viewPercent + zoomOutDelta);
	}

	private void createZoomControls() {
		zoomInButton = new JButton("+");
		zoomOutButton = new JButton("-");
		viewCenterButton = new JButton("reset");

		zoomInButton.setBackground(Color.gray);
		zoomInButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				zoomIn();
			}
		});

		zoomOutButton.setBackground(Color.gray);
		zoomOutButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				zoomOut();
			}
		});

		viewCenterButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				view.getCamera().resetView();
			}
		});
	}

	private void createToggleLayoutButton()
	{
		toggleLayout = new JButton();
		toggleAutoLayout();
		toggleLayout.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				toggleAutoLayout();
			}
		});
	}

	private void toggleAutoLayout()
	{
		if(!autoLayoutEnabled)
		{
			if(viewer != null && graphLayout != null)
			{
				//				viewer.enableAutoLayout(graphLayout);
				experimentalLayout();
			}
			else if(viewer != null)
			{
				//				viewer.enableAutoLayout();
				experimentalLayout();
			}
			autoLayoutEnabled = true;
			toggleLayout.setText("Disable Layouting");
		}
		else
		{
			viewer.disableAutoLayout();
			autoLayoutEnabled = false;
			toggleLayout.setText("Enable Layouting");
		}
	}

	private void renderICFG(ICFGStructure icfg) {
		Iterator<VFMethodEdge> iterator = icfg.listEdges.iterator();
		try {
			reintializeGraph();
		} catch (Exception e) {
			e.printStackTrace();
		}
		while(iterator.hasNext())
		{
			VFMethodEdge curr = iterator.next();

			VFMethod src = curr.getSourceMethod();
			VFMethod dest = curr.getDestMethod();

			createGraphMethodNode(src);
			createGraphMethodNode(dest);
			createGraphMethodEdge(src, dest);
		}
		experimentalLayout();
	}

	private void createGraphMethodEdge(VFMethod src, VFMethod dest) {
		if(graph.getEdge("" + src.getId() + dest.getId()) == null)
		{
			graph.addEdge(src.getId() + "" + dest.getId(), src.getId() + "", dest.getId() + "", true);
		}
	}

	private void createGraphMethodNode(VFMethod src) {
		if(graph.getNode(src.getId() + "") == null)
		{
			Node createdNode = graph.addNode(src.getId() + "");
			createdNode.setAttribute("ui.label", src.getSootMethod().getName().toString());
			createdNode.setAttribute("nodeData.methodName", src.getSootMethod().getName());
			createdNode.setAttribute("nodeData.methodSignature", src.getSootMethod().getSignature());
			createdNode.setAttribute("nodeMethod", src);
		}
	}

	private void renderMethodCFG(ControlFlowGraph interGraph) throws Exception
	{
		if(interGraph == null)
			throw new Exception("GraphStructure is null");

		this.reintializeGraph();
		ListIterator<VFEdge> edgeIterator = interGraph.listEdges.listIterator();

		while(edgeIterator.hasNext())
		{
			VFEdge currEdgeIterator = edgeIterator.next();

			VFNode src = currEdgeIterator.getSource();
			VFNode dest = currEdgeIterator.getDestination();

			createGraphNode(src);
			createGraphNode(dest);
			createGraphEdge(src,dest);
		}
		experimentalLayout();
	}

	private void createGraphEdge(VFNode src, VFNode dest) {
		if(graph.getEdge("" + src.getId() + dest.getId()) == null)
		{
			Edge createdEdge = graph.addEdge(src.getId() + "" + dest.getId(), src.getId() + "", dest.getId() + "", true);
			createdEdge.addAttribute("ui.label", "{a,b}");
			createdEdge.addAttribute("edgeData.outSet", "{a,b}");
		}
	}

	private void createGraphNode(VFNode node) {
		int maxLength = 65;
		if(graph.getNode(node.getId() + "") == null)
		{
			Node createdNode = graph.addNode(node.getId() + "");
			if(node.getLabel().toString().length() > maxLength)
			{
				createdNode.setAttribute("ui.label", node.getLabel().toString().substring(0, maxLength) + "...");
			}
			else
				createdNode.setAttribute("ui.label", node.getLabel().toString());
			createdNode.setAttribute("nodeData.unit", node.getLabel().toString());
			createdNode.setAttribute("nodeData.unitType", node.getLabel().getClass());
			createdNode.setAttribute("nodeData.inSet", "coming soon");
			createdNode.setAttribute("nodeData.outSet", "coming soon");
		}
	}

	private void experimentalLayout()
	{
		double spacing = 2.0;
		double rowSpacing = 18.0;
		double nodeCount = graph.getNodeCount() * spacing;
		Iterator<Node> nodeIterator = graph.getNodeIterator();
		while(nodeIterator.hasNext())
		{
			Node curr = nodeIterator.next();
			if(curr.getId().contentEquals("showICFG"))
			{
				curr.setAttribute("xyz", 0.0, 0.0, 0.0);
				continue;
			}

			Iterator<Edge> leavingEdgeIterator = curr.getEdgeIterator();
			double outEdges = 0.0;
			while(leavingEdgeIterator.hasNext())
			{
				Edge outEdge = leavingEdgeIterator.next();
				Node target = outEdge.getTargetNode();
				target.setAttribute("xyz", outEdges, nodeCount, 0.0);
				outEdges += rowSpacing;
			}

			curr.setAttribute("xyz", 0.0, nodeCount, 0.0);
			nodeCount -= spacing;
		}
		view.getCamera().resetView();
	}

	void toggleNode(String id){
		System.out.println("Togglenodes called");
		Node n  = graph.getNode(id);
		Object[] pos = n.getAttribute("xyz");
		Iterator<Node> it = n.getBreadthFirstIterator(true);
		if(n.hasAttribute("collapsed")){
			n.removeAttribute("collapsed");
			while(it.hasNext()){
				Node m  =  it.next();

				for(Edge e : m.getLeavingEdgeSet()) {
					e.removeAttribute("ui.hide");
				}
				m.removeAttribute("layout.frozen");
				m.setAttribute("x",((double)pos[0])+Math.random()*0.0001);
				m.setAttribute("y",((double)pos[1])+Math.random()*0.0001);

				m.removeAttribute("ui.hide");

			}
			n.removeAttribute("ui.class");

		} else {
			n.setAttribute("ui.class", "plus");
			n.setAttribute("collapsed");

			while(it.hasNext()){
				Node m  =  it.next();

				for(Edge e : m.getLeavingEdgeSet()) {
					e.setAttribute("ui.hide");
				}
				if(n != m) {
					m.setAttribute("layout.frozen");
					//					m.setAttribute("x", ((double) pos[0]) + Math.random() * 0.0001);
					//					m.setAttribute("y", ((double) pos[1]) + Math.random() * 0.0001);

					m.setAttribute("xyz", ((double) pos[0]) + Math.random() * 0.0001, ((double) pos[1]) + Math.random() * 0.0001, 0.0);

					m.setAttribute("ui.hide");
				}

			}
		}
	}

	@Override
	public void run() {
		ViewerPipe fromViewer = viewer.newViewerPipe();
		fromViewer.addViewerListener(this);
		fromViewer.addSink(graph);

		EventHandler dataModelHandler = new EventHandler() {
			@Override
			public void handleEvent(Event event) {
				if(event.getTopic().equals(DataModel.EA_TOPIC_DATA_SELECTION))
				{
					VFMethod selectedMethod = (VFMethod) event.getProperty("selectedMethod");
					try {
						renderMethodCFG(selectedMethod.getControlFlowGraph());
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				else if(event.getTopic().equals(DataModel.EA_TOPIC_DATA_MODEL_CHANGED))
				{
					renderICFG((ICFGStructure) event.getProperty("icfg"));
				}
			}
		};
		Hashtable<String, String> properties = new Hashtable<String, String>();
		properties.put(EventConstants.EVENT_TOPIC, DataModel.EA_TOPIC_DATA_SELECTION);
		properties.put(EventConstants.EVENT_TOPIC, DataModel.EA_TOPIC_DATA_MODEL_CHANGED);
		ServiceUtil.registerService(EventHandler.class, dataModelHandler, properties);

		// FIXME the Thread.sleep slows down the loop, so that it does not eat up the CPU
		// but this really should be implemented differently. isn't there an event listener
		// or something we can use, so that we call pump() only when necessary
		while(true) {
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
			}
			fromViewer.pump();
		}
	}

	@Override
	public void buttonPushed(String id) {
		//noop
	}

	@Override
	public void buttonReleased(String id) {
		if(id.contains("showICFG"))
		{
			renderICFG(ServiceUtil.getService(DataModel.class).getIcfg());
			return;
		}
		toggleNode(id);
		experimentalLayout();
	}

	@Override
	public void viewClosed(String id) {
		//noop
	}

	protected void setTip(JToolTip toolTip) {
		this.tip = toolTip;
	}


}