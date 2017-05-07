package de.privatepublic.pi.synth;

import java.awt.Desktop;
import java.awt.Font;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;

import javax.swing.SwingConstants;
import javax.swing.border.BevelBorder;

public class AppWindow {

	private static final Logger log = LoggerFactory.getLogger(AppWindow.class);
	
	private JFrame frame;
	private JTextArea textArea;
	private JProgressBar progressBarLoad;
	private JLabel lblLCDLabel;

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
		
		lblLCDLabel = new JLabel("<html>1234567890123456<br>1234567890123456</html>");
		lblLCDLabel.setBorder(new BevelBorder(BevelBorder.LOWERED, null, null, null, null));
		lblLCDLabel.setForeground(Color.GREEN);
		lblLCDLabel.setOpaque(true);
		lblLCDLabel.setHorizontalAlignment(SwingConstants.CENTER);
		lblLCDLabel.setFont(new Font("Monospaced", lblLCDLabel.getFont().getStyle(), 17));
		lblLCDLabel.setBackground(Color.decode("#484848"));
		GroupLayout groupLayout = new GroupLayout(frame.getContentPane());
		groupLayout.setHorizontalGroup(
			groupLayout.createParallelGroup(Alignment.TRAILING)
				.addGroup(groupLayout.createSequentialGroup()
					.addGroup(groupLayout.createParallelGroup(Alignment.LEADING)
						.addGroup(groupLayout.createSequentialGroup()
							.addGap(15)
							.addComponent(lblSynthpi)
							.addPreferredGap(ComponentPlacement.RELATED, 129, Short.MAX_VALUE)
							.addComponent(lblDspLoad)
							.addPreferredGap(ComponentPlacement.RELATED)
							.addComponent(progressBarLoad, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
						.addGroup(groupLayout.createSequentialGroup()
							.addContainerGap()
							.addComponent(scrollPane))
						.addGroup(groupLayout.createSequentialGroup()
							.addContainerGap()
							.addGroup(groupLayout.createParallelGroup(Alignment.LEADING)
								.addComponent(lblLCDLabel, GroupLayout.DEFAULT_SIZE, 438, Short.MAX_VALUE)
								.addGroup(groupLayout.createSequentialGroup()
									.addComponent(btnOpenBrowserInterface)
									.addPreferredGap(ComponentPlacement.RELATED, 174, Short.MAX_VALUE)
									.addComponent(btnExit)))))
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
					.addGap(9)
					.addComponent(scrollPane, GroupLayout.DEFAULT_SIZE, 159, Short.MAX_VALUE)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addGroup(groupLayout.createParallelGroup(Alignment.BASELINE)
						.addComponent(btnOpenBrowserInterface)
						.addComponent(btnExit))
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(lblLCDLabel, GroupLayout.PREFERRED_SIZE, 42, GroupLayout.PREFERRED_SIZE)
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
	
	public void lcdMessage(String line1, String line2, Color color) {
		line1 = StringUtils.rightPad(line1, 16);
		line2 = StringUtils.rightPad(line2, 16);
		if (line1.length()>16) { line1 = line1.substring(0, 16); }
		if (line2.length()>16) { line2 = line2.substring(0, 16); }
		line1 = line1.replaceAll(" ", "&nbsp;");
		line2 = line2.replaceAll(" ", "&nbsp;");;
		lblLCDLabel.setText("<html>"+line1+"<br>"+line2+"</html>");
		lblLCDLabel.setForeground(color);
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
				List<String> cmdParts = new ArrayList<String>();
				Matcher m = Pattern.compile("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'").matcher(P.CUSTOM_BROWSER_COMMAND.concat(" ").concat(uri));
				while (m.find()) {
				    if (m.group(1) != null) {
				        cmdParts.add(m.group(1)); // "
				    } else if (m.group(2) != null) {
				        cmdParts.add(m.group(2)); // '
				    } else {
				        cmdParts.add(m.group());
				    }
				} 
				Runtime.getRuntime().exec(cmdParts.toArray(new String[cmdParts.size()]));
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
