package de.privatepublic.pi.synth;

import java.awt.Desktop;
import java.awt.Font;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Method;
import java.net.URI;

import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.LayoutStyle.ComponentPlacement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;

public class AppWindow {

	private static final Logger log = LoggerFactory.getLogger(AppWindow.class);
	
	private JFrame frame;
	private JTextArea textArea;
	private JProgressBar progressBarLoad;

	/**
	 * Create the application.
	 */
	public AppWindow() {
		try {
			// Set cross-platform Java L&F (also called "Metal")
			UIManager.setLookAndFeel(
					UIManager.getSystemLookAndFeelClassName());
		} 
		catch (UnsupportedLookAndFeelException e) {
			// handle exception
		}
		catch (ClassNotFoundException e) {
			// handle exception
		}
		catch (InstantiationException e) {
			// handle exception
		}
		catch (IllegalAccessException e) {
			// handle exception
		}
		initialize();
		frame.setVisible(true);
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		
		frame = new JFrame();
		frame.setBounds(100, 100, 450, 300);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setTitle("SynthPi Control");
		setIcon(frame);
		JButton btnExit = new JButton("Exit");
		JButton btnOpenBrowserInterface = new JButton("Open Browser Interface");
		
		JLabel lblSynthpi = new JLabel("SynthPi");
		lblSynthpi.setIcon(new ImageIcon(AppWindow.class.getResource("/webresources/img/favicon.png")));
		lblSynthpi.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
		
		JScrollPane scrollPane = new JScrollPane();
		
		progressBarLoad = new JProgressBar();
		
		JLabel lblDspLoad = new JLabel("DSP load:");
		lblDspLoad.setLabelFor(progressBarLoad);
		lblDspLoad.setFont(new Font("Dialog", Font.PLAIN, 12));
		GroupLayout groupLayout = new GroupLayout(frame.getContentPane());
		groupLayout.setHorizontalGroup(
			groupLayout.createParallelGroup(Alignment.TRAILING)
				.addGroup(groupLayout.createSequentialGroup()
					.addGroup(groupLayout.createParallelGroup(Alignment.LEADING)
						.addGroup(groupLayout.createSequentialGroup()
							.addGap(15)
							.addComponent(lblSynthpi)
							.addPreferredGap(ComponentPlacement.RELATED, 107, Short.MAX_VALUE)
							.addComponent(lblDspLoad)
							.addPreferredGap(ComponentPlacement.RELATED)
							.addComponent(progressBarLoad, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
						.addGroup(groupLayout.createSequentialGroup()
							.addContainerGap()
							.addComponent(btnOpenBrowserInterface)
							.addPreferredGap(ComponentPlacement.RELATED, 162, Short.MAX_VALUE)
							.addComponent(btnExit))
						.addGroup(groupLayout.createSequentialGroup()
							.addContainerGap()
							.addComponent(scrollPane, GroupLayout.DEFAULT_SIZE, 424, Short.MAX_VALUE)))
					.addContainerGap())
		);
		groupLayout.setVerticalGroup(
			groupLayout.createParallelGroup(Alignment.TRAILING)
				.addGroup(groupLayout.createSequentialGroup()
					.addGroup(groupLayout.createParallelGroup(Alignment.LEADING)
						.addGroup(groupLayout.createSequentialGroup()
							.addGap(24)
							.addComponent(progressBarLoad, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
						.addGroup(groupLayout.createSequentialGroup()
							.addGap(15)
							.addGroup(groupLayout.createParallelGroup(Alignment.BASELINE)
								.addComponent(lblSynthpi)
								.addComponent(lblDspLoad))))
					.addGap(18)
					.addComponent(scrollPane, GroupLayout.DEFAULT_SIZE, 160, Short.MAX_VALUE)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addGroup(groupLayout.createParallelGroup(Alignment.LEADING)
						.addComponent(btnExit)
						.addComponent(btnOpenBrowserInterface))
					.addContainerGap())
		);
		textArea = new JTextArea();
		textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setEditable(false);
		scrollPane.setViewportView(textArea);
		
		btnExit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				System.exit(0);
			}
		});
		btnOpenBrowserInterface.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				AppWindow.openWebBrowser();
			}
		});
		
		frame.getContentPane().setLayout(groupLayout);
	}
	
	public void appendMessage(String message) {
		textArea.append(message);
		textArea.setCaretPosition(textArea.getText().length());
	}
	
	public void setLoad(float load) {
		progressBarLoad.setValue((int)(100*load));
	}
	
	public static void openWebBrowser() {
		try {
			
			String uri = "http://localhost:" + P.PORT_HTTP;
			
			if (P.CUSTOM_BROWSER_COMMAND!=null) {
				log.info("Starting browser: {} {}", P.CUSTOM_BROWSER_COMMAND, uri);
				SynthPi.uiMessage("Starting browser "+P.CUSTOM_BROWSER_COMMAND+" "+uri);
				Runtime.getRuntime().exec(P.CUSTOM_BROWSER_COMMAND.concat(" ").concat(uri).split(" "));
			}
			else {
				log.info("Starting desktop browser...");
				SynthPi.uiMessage("Starting desktop browser with control interface: "+uri);
				String os = System.getProperty("os.name").toLowerCase();
				if (os.contains("mac")) {
					Runtime.getRuntime().exec(new String[] {"osascript", "-e", "open location \"http://localhost:" + P.PORT_HTTP + "\""});
				}
				else {
					Desktop.getDesktop().browse(new URI(uri));
				}
			}
		} catch (Exception ex) {
			log.warn("Could not start local web browser: {}", ex.getMessage());
		}
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void setIcon(JFrame frame) {
		frame.setIconImage(Toolkit.getDefaultToolkit().getImage(AppWindow.class.getResource("/webresources/img/favicon.png")));
		try {
			// set icon for mac os if possible
			Class c = Class.forName("com.apple.eawt.Application");
			Object app = c.getDeclaredMethod ("getApplication", (Class[])null).invoke(null, (Object[])null);
			Method setDockIconImage = c.getDeclaredMethod("setDockIconImage", Image.class);
			setDockIconImage.invoke(app, new ImageIcon(AppWindow.class.getResource("/webresources/img/favicon.png")).getImage());
		} catch (Exception e) {
			// fail silently
		}
	}
}
