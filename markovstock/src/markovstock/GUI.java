package markovstock;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.ui.RefineryUtilities;

public class GUI extends JFrame implements ActionListener {
	private JTextField company = new JTextField(10);
	private JTextField numSims = new JTextField(10);
	private JTextField numDays = new JTextField(10);
	private JButton loadData = new JButton("Load Data");
	private JButton startSims = new JButton("Start Simulations");
	private JButton exportSims = new JButton("Export Simulations");
	private JLabel companyLabel = new JLabel("Ticker Symbol:");
	private JLabel numSimsLabel = new JLabel("Number of simulations:");
	private JLabel numDaysLabel = new JLabel("Number of days:");
	private JLabel status = new JLabel("Status: None");
	
	private String[] stateChoices = {"Small increase", "Large increase", "Small decrease", "Large decrease", "No change"};
	private JComboBox startList = new JComboBox(stateChoices);
	private JComboBox endList = new JComboBox(stateChoices);
	private JTextField numDaysLater = new JTextField(10);
	private JButton startChance = new JButton("Check Probability");
	private JLabel startListLabel = new JLabel("Starting state:");
	private JLabel endListLabel = new JLabel("Ending state:");
	private JLabel numDaysLaterLabel = new JLabel("Number of days later:");
	private JLabel predictedChance = new JLabel("Probability: ");
	
	private JTabbedPane tabbedContent = new JTabbedPane();
	private TimeSeriesCollection collection = new TimeSeriesCollection();
	
	public GUI(String title){
		super(title);
		setContentPane(tabbedContent);
		
		JPanel[] buttons = initializeButtons();
		JPanel[] forms = initializeForms();
		JPanel[] labels = initializeLabels();
		JPanel tab1sub = new JPanel();
		tab1sub.add(labels[0]);
		tab1sub.add(forms[0]);
		tab1sub.add(buttons[0]);
		JPanel tab1sup = new JPanel();
		tab1sup.add(new JLabel(new ImageIcon("./img/logo.png")));
		JPanel tab1 = new JPanel();
		tab1.setLayout(new BoxLayout(tab1, BoxLayout.Y_AXIS));
		tab1.add(tab1sup);
		tab1.add(tab1sub);
		
		JFreeChart chart = ChartFactory.createTimeSeriesChart(
				"Simulations", "Time", "Price", collection, false, false, false);
		ChartPanel chartPanel = new ChartPanel(chart);
		XYPlot plot = chart.getXYPlot();
        ValueAxis axis = plot.getDomainAxis();
        axis.setAutoRange(true);
        axis = plot.getRangeAxis();
        axis.setAutoRange(true);; 
		JPanel tab2 = new JPanel();
		tab2.add(chartPanel);
		
		JPanel tab3 = new JPanel();
		tab3.add(labels[1]);
		tab3.add(forms[1]);
		tab3.add(buttons[1]);
		tabbedContent.addTab("Configuration", tab1);
		tabbedContent.addTab("Simulations", tab2);
		tabbedContent.addTab("Targeted Chances", tab3);
		
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter(){
			public void windowClosing(WindowEvent e){
				int response = JOptionPane.showConfirmDialog(null, 
						"Simulations will only be persisted if exported.\nDo you really want to leave?", 
						"Confim Exit",
				        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
				if (response == JOptionPane.YES_OPTION){
					File matricesCSV = new File("matrices.csv");
					matricesCSV.delete();
					File database = new File("sims.db");
					database.delete();
					System.exit(0);
				}
			}
		});
	}
	
	@Override
	public void actionPerformed(ActionEvent action){
		if (action.getActionCommand().equals("Load")){
			String companyText = company.getText().trim();
			if (!companyText.isEmpty()){
				if (isTextValid(companyText)){
					try{
						ArrayList<String[]> records = ParseData.obtainRecords(companyText);
						ParseData.createParameters(companyText, records);
						JOptionPane.showMessageDialog(null,	"Data loaded.");
						status.setText("Status: Information loaded.");
					}
					catch (Exception e){
						e.printStackTrace();
						JOptionPane.showMessageDialog(null,	"An error has occurred.");
					}
				}
				else{
					JOptionPane.showMessageDialog(null,	"Fields must be of correct type.");
				}
			}
			else{
				JOptionPane.showMessageDialog(null,	"Fields cannot be empty.");
			}
		}
		else if (action.getActionCommand().equals("Simulate")) {
			String companyText = company.getText().trim();
			String numSimsText = numSims.getText().trim();
			String numDaysText = numDays.getText().trim();
			if (!companyText.isEmpty() && !numSimsText.isEmpty() && !numDaysText.isEmpty()){
				if (isNumValid(numSimsText) && isNumValid(numDaysText) && isTextValid(companyText)){
					int numberSims = Integer.parseInt(numSimsText);
					int numberDays = Integer.parseInt(numDaysText);
					try{
						JDialog loadingDialog = new JDialog(this, "Progress dialog", true);
						loadingDialog.add(new JLabel("Loading..."), BorderLayout.PAGE_START);
						JProgressBar progress = new JProgressBar();
						progress.setIndeterminate(true);
						loadingDialog.add(progress, BorderLayout.CENTER);
						loadingDialog.setSize(300, 100);
						loadingDialog.setLocationRelativeTo(null);
						loadingDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
						SwingWorker simWorker = new SwingWorker<Void, Void>(){
							@Override
							protected Void doInBackground() throws Exception {
								MarkovChain userMC = new MarkovChain(companyText);
								double currentPrice = userMC.getCurentPrice();
								userMC.eraseSimulations();
								userMC.doSimulations(numberSims, numberDays, currentPrice);
								userMC.extractSimulations(currentPrice, collection);
								status.setText("Status: Simulations plotted.");
								return null;
							}
							
							@Override
							protected void done(){
								loadingDialog.dispose();
							}
						};
						simWorker.execute();
						loadingDialog.setVisible(true);
						JOptionPane.showMessageDialog(null,	"Simulations plotted. Check the second tab.");
					}
					catch (NullPointerException e){
						JOptionPane.showMessageDialog(null,	"Load Data before Simulating.");
					}
					catch (Exception e){
						e.printStackTrace();
						JOptionPane.showMessageDialog(null,	"An error has occurred.");
					}
				}
				else{
					JOptionPane.showMessageDialog(null,	"Fields must be of correct type.");
				}
			}
			else{
				JOptionPane.showMessageDialog(null,	"Fields cannot be empty.");
			}
        }
		else if (action.getActionCommand().equals("Export")){
			String companyText = company.getText().trim();
			if (!companyText.isEmpty()){
				if (isTextValid(companyText)){
					try{
						MarkovChain userMC = new MarkovChain(companyText);
						userMC.exportSimulations();
						status.setText("Status: Simulations exported.");
						JOptionPane.showMessageDialog(null,	"Results exported to 'results' folder.");
					}
					catch (Exception e){
						e.printStackTrace();
						JOptionPane.showMessageDialog(null,	"An error has occurred.");
					}
				}
				else{
					JOptionPane.showMessageDialog(null,	"Fields must be of correct type.");
				}
			}
			else{
				JOptionPane.showMessageDialog(null,	"Fields cannot be empty.");
			}
		}
		else if (action.getActionCommand().equals("Chance")){
			String companyText = company.getText().trim();
			String numDaysLaterText = numDaysLater.getText().trim();
			if (!numDaysLaterText.isEmpty() && !companyText.isEmpty()){
				if (isNumValid(numDaysLaterText) && isTextValid(companyText)){
					int numberDaysLater = Integer.parseInt(numDaysLaterText);
					try{
						MarkovChain userMC = new MarkovChain(companyText);
						int initial = startList.getSelectedIndex(); 
						int ending = endList.getSelectedIndex();
						double chance = userMC.predictXDays(initial, ending, numberDaysLater);
						predictedChance.setText("Probability: " + chance);
					}
					catch (Exception e){
						System.out.println(e.getStackTrace());
						JOptionPane.showMessageDialog(null,	"An error has occurred.");
					}
				}
				else{
					JOptionPane.showMessageDialog(null,	"Fields must be of correct type.");
				}
			}
			else{
				JOptionPane.showMessageDialog(null,	"Fields cannot be empty.");
			}
		}
	}
	
	public JPanel[] initializeButtons(){
		JPanel buttonPanelP1 = new JPanel();
		JPanel buttonPanelP2 = new JPanel();
		buttonPanelP1.setLayout(new BoxLayout(buttonPanelP1, BoxLayout.X_AXIS));
		buttonPanelP2.setLayout(new BoxLayout(buttonPanelP2, BoxLayout.X_AXIS));
		buttonPanelP1.add(loadData);
		buttonPanelP1.add(Box.createRigidArea(new Dimension(50,0)));
		buttonPanelP1.add(startSims);
		buttonPanelP1.add(Box.createRigidArea(new Dimension(50,0)));
		buttonPanelP1.add(exportSims);
		buttonPanelP1.add(Box.createRigidArea(new Dimension(50,0)));
		buttonPanelP2.add(Box.createRigidArea(new Dimension(50,0)));
		buttonPanelP2.add(startChance);
		buttonPanelP2.add(Box.createRigidArea(new Dimension(50,0)));
		loadData.setActionCommand("Load");
	    loadData.addActionListener(this);
		startSims.setActionCommand("Simulate");
	    startSims.addActionListener(this);
	    exportSims.setActionCommand("Export");
	    exportSims.addActionListener(this);
	    startChance.setActionCommand("Chance");
	    startChance.addActionListener(this);
	    buttonPanelP1.setBorder(new EmptyBorder(0, 50, 400, 0));
	    buttonPanelP2.setBorder(new EmptyBorder(50, 50, 0, 0));
	    JPanel[] buttonPanels = {buttonPanelP1, buttonPanelP2};
	    return buttonPanels;
	}
	
	public JPanel[] initializeForms(){
		JPanel formPanelP1 = new JPanel(); 
		JPanel formPanelP2 = new JPanel();
		formPanelP1.setLayout(new BoxLayout(formPanelP1, BoxLayout.Y_AXIS));
		formPanelP2.setLayout(new BoxLayout(formPanelP2, BoxLayout.Y_AXIS));
		formPanelP1.add(Box.createRigidArea(new Dimension(0,10)));
		formPanelP1.add(company);
		formPanelP1.add(Box.createRigidArea(new Dimension(0,50)));
		formPanelP1.add(numSims);
		formPanelP1.add(Box.createRigidArea(new Dimension(0,50)));
		formPanelP1.add(numDays);
		formPanelP1.add(Box.createRigidArea(new Dimension(0,50)));
		formPanelP2.add(Box.createRigidArea(new Dimension(0,50)));
		formPanelP2.add(startList);
		formPanelP2.add(Box.createRigidArea(new Dimension(0,50)));
		formPanelP2.add(endList);
		formPanelP2.add(Box.createRigidArea(new Dimension(0,50)));
		formPanelP2.add(numDaysLater);
		formPanelP2.add(Box.createRigidArea(new Dimension(0,50)));
		formPanelP1.setBorder(new EmptyBorder(0, 0, 100, 0));
		formPanelP1.setPreferredSize(new Dimension(100, 340));
		formPanelP2.setBorder(new EmptyBorder(10, 0, 10, 0));
		formPanelP2.setPreferredSize(new Dimension(150, 300));
		JPanel[] formPanels = {formPanelP1, formPanelP2};
		return formPanels;
	}
	
	public JPanel[] initializeLabels(){
		JPanel labelPanelP1 = new JPanel();
		JPanel labelPanelP2 = new JPanel();
		labelPanelP1.setLayout(new BoxLayout(labelPanelP1, BoxLayout.Y_AXIS));
		labelPanelP2.setLayout(new BoxLayout(labelPanelP2, BoxLayout.Y_AXIS));
		labelPanelP1.add(Box.createRigidArea(new Dimension(0,15)));
		labelPanelP1.add(companyLabel);
		companyLabel.setFont(new Font("Arial", Font.BOLD, 16));
		labelPanelP1.add(Box.createRigidArea(new Dimension(0,60)));
		labelPanelP1.add(numSimsLabel);
		numSimsLabel.setFont(new Font("Arial", Font.BOLD, 16));
		labelPanelP1.add(Box.createRigidArea(new Dimension(0,60)));
		labelPanelP1.add(numDaysLabel);
		numDaysLabel.setFont(new Font("Arial", Font.BOLD, 16));
		labelPanelP1.add(Box.createRigidArea(new Dimension(0,60)));
		labelPanelP1.add(status);
		status.setForeground(Color.red);
		labelPanelP2.add(Box.createRigidArea(new Dimension(0,60)));
		labelPanelP2.add(startListLabel);
		startListLabel.setFont(new Font("Arial", Font.BOLD, 16));
		labelPanelP2.add(Box.createRigidArea(new Dimension(0,60)));
		labelPanelP2.add(endListLabel);
		endListLabel.setFont(new Font("Arial", Font.BOLD, 16));
		labelPanelP2.add(Box.createRigidArea(new Dimension(0,60)));
		labelPanelP2.add(numDaysLaterLabel);
		numDaysLaterLabel.setFont(new Font("Arial", Font.BOLD, 16));
		labelPanelP2.add(Box.createRigidArea(new Dimension(0,60)));
		labelPanelP2.add(predictedChance);
		predictedChance.setFont(new Font("Arial", Font.BOLD, 16));
		labelPanelP1.setBorder(new EmptyBorder(0, 50, 0, 0));
		labelPanelP1.setPreferredSize(new Dimension(250, 350));
		labelPanelP2.setBorder(new EmptyBorder(50, 50, 0, 0));
		labelPanelP2.setPreferredSize(new Dimension(250, 400));
		JPanel[] labelPanels = {labelPanelP1, labelPanelP2};
		return labelPanels;
	}

	public boolean isNumValid(String input){
		try{
			double temp = Double.parseDouble(input);
		}
		catch (NumberFormatException e){
			return false;
		}
		return true;
	}
	
	public boolean isTextValid(String input){
		for (char c: input.toCharArray()){
			if (!Character.isLetter(c)){
				return false;
			}
		}
		return true;
	}

	public static void main(String[] args){
		try{
			UIManager.setLookAndFeel("com.seaglasslookandfeel.SeaGlassLookAndFeel");
		}
		catch (Exception e) { 
			//do nothing - will use default if cannot set
		}
		GUI current = new GUI("Stock Simulation and Analysis Desktop Application by d-soni");
		current.setResizable(false);
		current.setSize(800, 600);
		RefineryUtilities.centerFrameOnScreen(current);
        current.setVisible(true);
	}
}
