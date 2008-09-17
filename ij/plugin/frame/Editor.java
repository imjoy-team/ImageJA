package ij.plugin.frame;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.awt.datatransfer.*;																																																																																													
import java.lang.reflect.*;
import ij.*;
import ij.gui.*;
import ij.util.Tools;
import ij.text.TextWindow;
import ij.macro.*;
import ij.plugin.MacroInstaller;
import ij.plugin.NewPlugin;
import ij.io.SaveDialog;


/** This is a simple TextArea based editor for editing and compiling plugins. */
public class Editor extends PlugInFrame implements ActionListener, ItemListener,
	TextListener, ClipboardOwner, MacroConstants {
	
	public static String JavaScriptIncludes =
		"importPackage(Packages.ij);"+
		"importPackage(Packages.ij.gui);"+
		"importPackage(Packages.ij.process);"+
		"importPackage(Packages.ij.measure);"+
		"importPackage(java.lang);"+
		"importPackage(java.awt);"+
		"function print(s) {IJ.log(s);};";
	public static String JS_NOT_FOUND = 
		"JavaScript.jar was not found in the plugins\nfolder. It can be downloaded from:\n \nrsb.info.nih.gov/ij/download/tools/JavaScript.jar";
	public static final int MAX_SIZE=28000, XINC=10, YINC=18;
	public static final int MONOSPACED=1, MENU_BAR=2;
	public static final int MACROS_MENU_ITEMS = 6;
	static final String FONT_SIZE = "editor.font.size";
	static final String FONT_MONO= "editor.font.mono";
	private TextArea ta;
	private String path;
	private boolean changes;
	private static String searchString = "";
	private static int lineNumber = 1;
	private static int xoffset, yoffset;
	private static int nWindows;
	private Menu fileMenu, editMenu;
	private Properties p = new Properties();
	private int[] macroStarts;
	private String[] macroNames;
	private MenuBar mb;
	private Menu macrosMenu;
	private int nMacros;
	private Program pgm;
	private int eventCount;
	private String shortcutsInUse;
	private int inUseCount;
	private MacroInstaller installer;
	private static String defaultDir;
	private boolean dontShowWindow;
    private int[] sizes = {9, 10, 11, 12, 13, 14, 16, 18, 20, 24, 36, 48, 60, 72};
    private int fontSize = (int)Prefs.get(FONT_SIZE, 5);
    private CheckboxMenuItem monospaced;
    private static boolean caseSensitive = true;
    private static boolean wholeWords;
    private boolean isMacroWindow;
	
	public Editor() {
		this(16, 60, 0, MENU_BAR);
	}

	public Editor(int rows, int columns, int fontSize, int options) {
		super("Editor");
		WindowManager.addWindow(this);
		addMenuBar(options);	
		ta = new TextArea(rows, columns);
		ta.addTextListener(this);
		if (IJ.isLinux()) ta.setBackground(Color.white);
 		addKeyListener(IJ.getInstance());  // ImageJ handles keyboard shortcuts
		add(ta);
		pack();
		if (fontSize<0) fontSize = 0;
		if (fontSize>=sizes.length) fontSize = sizes.length-1;
        setFont();
		positionWindow();
		//display("Test.java", "");
		IJ.register(Editor.class);
	}
	
	void addMenuBar(int options) {
		mb = new MenuBar();
		if (Menus.getFontSize()!=0) ;
			mb.setFont(Menus.getFont());
		Menu m = new Menu("File");
		m.add(new MenuItem("New...", new MenuShortcut(KeyEvent.VK_N, true)));
		m.add(new MenuItem("Open...", new MenuShortcut(KeyEvent.VK_O)));
		m.add(new MenuItem("Save", new MenuShortcut(KeyEvent.VK_S)));
		m.add(new MenuItem("Save As..."));
		m.add(new MenuItem("Print...", new MenuShortcut(KeyEvent.VK_P)));
		m.addActionListener(this);
		fileMenu = m;
		mb.add(m);
		
		m = new Menu("Edit");
		String key = IJ.isMacintosh()?"  Cmd ":"  Ctrl+";
		MenuItem item = new MenuItem("Undo"+key+"Z");
		item.setEnabled(false);
		m.add(item);
		m.addSeparator();
		boolean shortcutsBroken = IJ.isWindows()
			&& (System.getProperty("java.version").indexOf("1.1.8")>=0
			||System.getProperty("java.version").indexOf("1.5.")>=0);
		if (shortcutsBroken)
			item = new MenuItem("Cut  Ctrl+X");
		else
			item = new MenuItem("Cut",new MenuShortcut(KeyEvent.VK_X));
		m.add(item);
		if (shortcutsBroken)
			item = new MenuItem("Copy  Ctrl+C");
		else
			item = new MenuItem("Copy", new MenuShortcut(KeyEvent.VK_C));
		m.add(item);
		if (shortcutsBroken)
			item = new MenuItem("Paste  Ctrl+V");
		else
			item = new MenuItem("Paste",new MenuShortcut(KeyEvent.VK_V));
		m.add(item);
		m.addSeparator();
		m.add(new MenuItem("Find...", new MenuShortcut(KeyEvent.VK_F)));
		m.add(new MenuItem("Find Next", new MenuShortcut(KeyEvent.VK_G)));
		m.add(new MenuItem("Go to Line...", new MenuShortcut(KeyEvent.VK_L)));
		m.addSeparator();
		m.add(new MenuItem("Select All", new MenuShortcut(KeyEvent.VK_A)));
		m.add(new MenuItem("Zap Gremlins"));
		m.addActionListener(this);
		mb.add(m);
		editMenu = m;
		if ((options&MENU_BAR)!=0)
			setMenuBar(mb);
		
		m = new Menu("Font");
		m.add(new MenuItem("Make Text Smaller", new MenuShortcut(KeyEvent.VK_N)));
		m.add(new MenuItem("Make Text Larger", new MenuShortcut(KeyEvent.VK_M)));
		m.addSeparator();
		monospaced = new CheckboxMenuItem("Monospaced Font", Prefs.get(FONT_MONO, false));
		if ((options&MONOSPACED)!=0) monospaced.setState(true);
		monospaced.addItemListener(this);
		m.add(monospaced);
		m.add(new MenuItem("Save Settings"));
		m.addActionListener(this);
		mb.add(m);
	}
			
	public void positionWindow() {
		Dimension screen = IJ.getScreenSize();
		Dimension window = getSize();
		if (window.width==0)
			return;
		int left = screen.width/2-window.width/2;
		int top = (screen.height-window.height)/4;
		if (top<0) top = 0;
		if (nWindows<=0 || xoffset>8*XINC)
			{xoffset=0; yoffset=0;}
		setLocation(left+xoffset, top+yoffset);
		xoffset+=XINC; yoffset+=YINC;
		nWindows++;
	}

	void setWindowTitle(String title) {
		Menus.updateWindowMenuItem(getTitle(), title);
		setTitle(title);
	}
	
	public void create(String name, String text) {
		ta.append(text);
		if (IJ.isMacOSX()) IJ.wait(25); // needed to get setCaretPosition() on OS X
		ta.setCaretPosition(0);
		setWindowTitle(name);
		if (name.endsWith(".txt") || name.endsWith(".ijm") || name.endsWith(".js")|| name.indexOf(".")==-1) {
			macrosMenu = new Menu("Macros");			
			macrosMenu.add(new MenuItem("Run Macro", new MenuShortcut(KeyEvent.VK_R)));
			macrosMenu.add(new MenuItem("Evaluate Line", new MenuShortcut(KeyEvent.VK_E)));
			macrosMenu.add(new MenuItem("Abort Macro"));
			macrosMenu.add(new MenuItem("Install Macros", new MenuShortcut(KeyEvent.VK_I)));
			macrosMenu.add(new MenuItem("Function Finder...", new MenuShortcut(KeyEvent.VK_F, true)));
			macrosMenu.addSeparator();
			macrosMenu.add(new MenuItem("Evaluate JavaScript", new MenuShortcut(KeyEvent.VK_J, false)));
			macrosMenu.addSeparator();
			// MACROS_MENU_ITEMS must be updated if items are added to this menu
			macrosMenu.addActionListener(this);
			mb.add(macrosMenu);
			if (text.indexOf("macro ")!=-1)
				installMacros(text, false);				
		} else {
			fileMenu.addSeparator();
			fileMenu.add(new MenuItem("Compile and Run", new MenuShortcut(KeyEvent.VK_R)));
		}
		if (IJ.getInstance()!=null && !dontShowWindow)
			show();
		if (dontShowWindow) {
			dispose();
			dontShowWindow = false;
		}
		WindowManager.setWindow(this);
		changes = false;
	}

	public void createMacro(String name, String text) {
		create(name, text);
		editMenu.add(new MenuItem("Convert to Plugin"));
	}

	void installMacros(String text, boolean installInPluginsMenu) {
		String functions = Interpreter.getAdditionalFunctions();
		if (functions!=null && text!=null) {
			if (!(text.endsWith("\n") || functions.startsWith("\n")))
				text = text + "\n" + functions;
			else
				text = text + functions;
		}
		installer = new MacroInstaller();
		installer.setFileName(getTitle());
		int nShortcutsOrTools = installer.install(text, macrosMenu);
		if (installInPluginsMenu || nShortcutsOrTools>0)
			installer.install(null);
		dontShowWindow = installer.isAutoRunAndHide();
	}
		
	public void open(String dir, String name) {
		path = dir+name;
		File file = new File(path);
		try {
			StringBuffer sb = new StringBuffer(5000);
			BufferedReader r = new BufferedReader(new FileReader(file));
			while (true) {
				String s=r.readLine();
				if (s==null)
					break;
				else
					sb.append(s+"\n");
			}
			r.close();
			create(name, new String(sb));
			changes = false;
		}
		catch (Exception e) {
			IJ.error(e.getMessage());
			return;
		}
	}

	public String getText() {
		if (ta==null)
			return "";
		else
			return ta.getText();
	}

	public TextArea getTextArea() {
		return ta;
	}

	public void display(String title, String text) {
		ta.selectAll();
		ta.replaceRange(text, ta.getSelectionStart(), ta.getSelectionEnd());
		ta.setCaretPosition(0);
		setWindowTitle(title);
		changes = false;
		if (IJ.getInstance()!=null)
			show();
		WindowManager.setWindow(this);
	}

	void save() {
		if (path==null) {
			saveAs(); 
			return;
		}
		File f = new File(path);
		if (f.exists() && !f.canWrite()) {
			IJ.showMessage("Editor", "Unable to save because file is write-protected. \n \n" + path);
			return;
		}
		String text = ta.getText();
		char[] chars = new char[text.length()];
		text.getChars(0, text.length(), chars, 0);
		try {
			BufferedReader br = new BufferedReader(new CharArrayReader(chars));
			BufferedWriter bw = new BufferedWriter(new FileWriter(path));
			while (true) {
				String s = br.readLine();
				if (s==null) break;
				bw.write(s, 0, s.length());
				bw.newLine();
			}
			bw.close();
			IJ.showStatus(text.length()+" chars saved to " + path);
			changes = false;
		} catch
			(IOException e) {}
	}

	void compileAndRun() {
		if (path==null)
			saveAs();
		if (path!=null) {
			save();
			IJ.runPlugIn("ij.plugin.Compiler", path);
		}
	}
	
	void runMacro() {
		if (getTitle().endsWith(".js"))
			{evaluateJavaScript(); return;}
		int start = ta.getSelectionStart();
		int end = ta.getSelectionEnd();
		String text;
		if (start==end)
			text = ta.getText();
		else
			text = ta.getSelectedText();
		new MacroRunner(text);
	}
	
	void evaluateJavaScript() {
		if (!getTitle().endsWith(".js"))
			setTitle(SaveDialog.setExtension(getTitle(), ".js"));
		int start = ta.getSelectionStart();
		int end = ta.getSelectionEnd();
		String text;
		if (start==end)
			text = ta.getText();
		else
			text = ta.getSelectedText();
		if (text.equals("")) return;
		if (IJ.isJava16() && !IJ.isMacOSX()) {
			IJ.runPlugIn("JavaScriptEvaluator", text);
			return;
		} else {
			Object js = IJ.runPlugIn("JavaScript", JavaScriptIncludes+text);
			if (js==null) IJ.error(JS_NOT_FOUND);
		}
	}

	void evaluateLine() {
		int start = ta.getSelectionStart();
		int end = ta.getSelectionEnd();
		if (end>start)
			{runMacro(); return;}
		String text = ta.getText();
		while (start>0) {
			start--;
			if (text.charAt(start)=='\n')
				{start++; break;}
		}
		while (end<text.length()-1) {
			end++;
			if (text.charAt(end)=='\n')
				break;
		}
		ta.setSelectionStart(start);
		ta.setSelectionEnd(end);
		runMacro();
	}

	void print () {
		PrintJob pjob = Toolkit.getDefaultToolkit().getPrintJob(this, "Cool Stuff", p);
		if (pjob != null) {
			Graphics pg = pjob.getGraphics( );
			if (pg != null) {
				String s = ta.getText();
				printString(pjob, pg, s);
				pg.dispose( );	
			}
			pjob.end( );
		}
	}

	void printString (PrintJob pjob, Graphics pg, String s) {
		int pageNum = 1;
		int linesForThisPage = 0;
		int linesForThisJob = 0;
		int topMargin = 30;
		int leftMargin = 30;
		int bottomMargin = 30;
		
		if (!(pg instanceof PrintGraphics))
			throw new IllegalArgumentException ("Graphics contextt not PrintGraphics");
		if (IJ.isMacintosh()) {
			topMargin = 0;
			leftMargin = 0;
			bottomMargin = 0;
		}
		StringReader sr = new StringReader (s);
		LineNumberReader lnr = new LineNumberReader (sr);
		String nextLine;
		int pageHeight = pjob.getPageDimension().height - bottomMargin;
		Font helv = new Font(getFontName(), Font.PLAIN, 10);
		pg.setFont (helv);
		FontMetrics fm = pg.getFontMetrics(helv);
		int fontHeight = fm.getHeight();
		int fontDescent = fm.getDescent();
		int curHeight = topMargin;
		try {
			do {
				nextLine = lnr.readLine();
			   if (nextLine != null) {		   
					nextLine = detabLine(nextLine);
					if ((curHeight + fontHeight) > pageHeight) {
						// New Page
						pageNum++;
						linesForThisPage = 0;
						pg.dispose();
						pg = pjob.getGraphics();
						if (pg != null)
							pg.setFont (helv);
						curHeight = topMargin;
					}
					curHeight += fontHeight;
					if (pg != null) {
						pg.drawString (nextLine, leftMargin, curHeight - fontDescent);
						linesForThisPage++;
						linesForThisJob++;
					} 
				}
			} while (nextLine != null);
		} catch (EOFException eof) {
	   // Fine, ignore
		} catch (Throwable t) { // Anything else
			t.printStackTrace();
		}
	}
	
	String detabLine(String s) {
		if (s.indexOf('\t')<0)
			return s;
		int tabSize = 4;
		StringBuffer sb = new StringBuffer((int)(s.length()*1.25));
		char c;
		for (int i=0; i<s.length(); i++) {
			c = s.charAt(i);
			if (c=='\t') {
				  for (int j=0; j<tabSize; j++)
					  sb.append(' '); 
		} else
			sb.append(c);
		 }
		return sb.toString();
  }	   

	boolean copy() { 
		String s; 
		s = ta.getSelectedText();
		Clipboard clip = getToolkit().getSystemClipboard();
		if (clip!=null) {
			StringSelection cont = new StringSelection(s);
			clip.setContents(cont,this);
			return true;
		} else
			return false;
	}
 
	  
	void cut() {
		if (copy()) {
			int start = ta.getSelectionStart();
			int end = ta.getSelectionEnd();
			ta.replaceRange("", start, end);
			if (IJ.isMacOSX())
				ta.setCaretPosition(start);
		}	
	}

	void paste() {
		String s;
		s = ta.getSelectedText();
		Clipboard clipboard = getToolkit( ). getSystemClipboard(); 
		Transferable clipData = clipboard.getContents(s);
		try {
			s = (String)(clipData.getTransferData(DataFlavor.stringFlavor));
		}
		catch  (Exception e)  {
			s  = e.toString( );
		}
		int start = ta.getSelectionStart( );
		int end = ta.getSelectionEnd( );
		ta.replaceRange(s, start, end);
		if (IJ.isMacOSX())
			ta.setCaretPosition(start+s.length());
	}

	public void actionPerformed(ActionEvent evt) {
		String what = evt.getActionCommand();
		if ("Save".equals(what))
			save();
		else if ("Compile and Run".equals(what))
				compileAndRun();
		else if ("Run Macro".equals(what))
				runMacro();
		else if ("Evaluate Line".equals(what))
				evaluateLine();
		else if ("Abort Macro".equals(what)) {
				Interpreter.abort();
				IJ.beep();		
		} else if ("Install Macros".equals(what))
				installMacros(ta.getText(), true);
		else if ("Function Finder...".equals(what))
			new FunctionFinder();
		else if ("Evaluate JavaScript".equals(what))
			evaluateJavaScript();
		else if ("Print...".equals(what))
			print();
		else if (what.startsWith("Paste"))
			paste();
		else if (what.startsWith("Copy"))
			copy();
		else if (what.startsWith("Cut"))
		   cut();
		else if ("Save As...".equals(what))
			saveAs();
		else if ("Select All".equals(what))
			selectAll();
		else if ("Find...".equals(what))
			find(null);
		else if ("Find Next".equals(what))
			find(searchString);
		else if ("Go to Line...".equals(what))
			gotoLine();
		else if ("Zap Gremlins".equals(what))
			zapGremlins();
		else if ("Convert to Plugin".equals(what))
			convertToPlugin();
		else if ("Make Text Larger".equals(what))
			changeFontSize(true);
		else if ("Make Text Smaller".equals(what))
			changeFontSize(false);
		else if ("Save Settings".equals(what))
			saveSettings();
		else if ("New...".equals(what))
			IJ.run("Text Window");
		else if ("Open...".equals(what))
			IJ.open();
		else
			installer.runMacro(what);
	}

	public void textValueChanged(TextEvent evt) {
		if (isMacroWindow) return;
		// first few textValueChanged events may be bogus
		eventCount++;
		if (eventCount>2 || !IJ.isMacOSX() && eventCount>1)
			changes = true;
		if (IJ.isMacOSX()) // screen update bug work around
			ta.setCaretPosition(ta.getCaretPosition());
	}

	public void itemStateChanged(ItemEvent e) {
		CheckboxMenuItem item = (CheckboxMenuItem)e.getSource();
        setFont();
	}

	/** Override windowActivated in PlugInFrame to
		prevent Mac menu bar from being installed. */
	public void windowActivated(WindowEvent e) {
		WindowManager.setWindow(this);
	}

	public void windowClosing(WindowEvent e) {
		close();
	}

	/** Overrides close() in PlugInFrame. */
	public void close() {
		boolean okayToClose = true;
		ImageJ ij = IJ.getInstance();
		if (!getTitle().equals("Errors") && changes && !IJ.isMacro() && ij!=null && !ij.quitting()) {
			String msg = "Save changes to \"" + getTitle() + "\"?";
			YesNoCancelDialog d = new YesNoCancelDialog(this, "Editor", msg);
			if (d.cancelPressed())
				okayToClose = false;
			else if (d.yesPressed())
				save();
		}
		if (okayToClose) {
			setVisible(false);
			dispose();
			WindowManager.removeWindow(this);
			nWindows--;
		}
	}

	void saveAs() {
		String name1 = getTitle();
		if (name1.indexOf(".")==-1) name1 += ".txt";
		if (defaultDir==null) {
			if (name1.endsWith(".txt")||name1.endsWith(".ijm"))
				defaultDir = Menus.getMacrosPath();
			else
				defaultDir = Menus.getPlugInsPath();
		}
		SaveDialog sd = new SaveDialog("Save As...", defaultDir, name1, null);
		String name2 = sd.getFileName();
		String dir = sd.getDirectory();
		if (name2!=null) {
			if (name2.endsWith(".java"))
				updateClassName(name1, name2);
			path = dir+name2;
			save();
			changes = false;
			setWindowTitle(name2);
		}
	}
	
	/** Changes a plugins class name to reflect a new file name. */
	public void updateClassName(String oldName, String newName) {
		if (newName.indexOf("_")<0)
			IJ.showMessage("Plugin Editor", "Plugins without an underscore in their name will not\n"
				+"be automatically installed when ImageJ is restarted.");
		if (oldName.equals(newName) || !oldName.endsWith(".java") || !newName.endsWith(".java"))
			return;
		oldName = oldName.substring(0,oldName.length()-5);
		newName = newName.substring(0,newName.length()-5);
		String text1 = ta.getText();
		int index = text1.indexOf("public class "+oldName);
		if (index<0)
			return;
		String text2 = text1.substring(0,index+13)+newName+text1.substring(index+13+oldName.length(),text1.length());
		ta.setText(text2);
	}
	
	void find(String s) {
		if (s==null) {
			GenericDialog gd = new GenericDialog("Find", this);
			gd.addStringField("Find: ", searchString, 20);
			String[] labels = {"Case Sensitive", "Whole Words"};
			boolean[] states = {caseSensitive, wholeWords};
			//boolean[] states = new boolean[2];
			//states[0]=caseSensitive; states[1]=wholeWords;
			gd.addCheckboxGroup(1, 2, labels, states);
			gd.showDialog();
			if (gd.wasCanceled())
				return;
			s = gd.getNextString();
			caseSensitive = gd.getNextBoolean();
			wholeWords = gd.getNextBoolean();
		}
		if (s.equals(""))
			return;
		String text = ta.getText();
		String s2 = s;
		if (!caseSensitive) {
			text = text.toLowerCase(Locale.US);
			s = s.toLowerCase(Locale.US);
		}
		int index = -1;
		if (wholeWords) {
			int position = ta.getCaretPosition()+1;
			while (true) {
				index = text.indexOf(s, position);
				if (index==-1) break;
				if (isWholeWordMatch(text, s, index)) break;
				position = index + 1;
				if (position>=text.length()-1)
					{index=-1; break;}
			}
		} else
			index = text.indexOf(s, ta.getCaretPosition()+1);
		searchString = s2;
		if (index<0)
			{IJ.beep(); return;}
		ta.setSelectionStart(index);
		ta.setSelectionEnd(index+s.length());
	}
	
	boolean isWholeWordMatch(String text, String word, int index) {
		char c = index==0?' ':text.charAt(index-1);
		if (Character.isLetterOrDigit(c) || c=='_') return false;
		c = index+word.length()>=text.length()?' ':text.charAt(index+word.length());
		if (Character.isLetterOrDigit(c) || c=='_') return false;
		return true;
	}
	
	void gotoLine() {
		GenericDialog gd = new GenericDialog("Go to Line", this);
		gd.addNumericField("Go to line number: ", lineNumber, 0);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		int n = (int)gd.getNextNumber();
		if (n<1) return;
		String text = ta.getText();
		char[] chars = new char[text.length()];
		chars = text.toCharArray();
		int count=1, loc=0;
		for (int i=0; i<chars.length; i++) {
			if (chars[i]=='\n') count++;
			if (count==n)
				{loc=i+1; break;}
		}
		ta.setCaretPosition(loc);
		lineNumber = n;
	}
	
	void zapGremlins() {
		String text = ta.getText();
		char[] chars = new char[text.length()];
		chars = text.toCharArray();
		int count=0;
		boolean inQuotes = false;
		char quoteChar = 0;
		for (int i=0; i<chars.length; i++) {
			char c = chars[i];
			if (!inQuotes && (c=='"' || c=='\'')) {
				inQuotes = true;
				quoteChar = c;
			} else  {
				if (inQuotes && (c==quoteChar || c=='\n'))
				inQuotes = false;
			}
			if (!inQuotes && c!='\n' && c!='\t' && (c<32||c>127)) {
				count++;
				chars[i] = ' ';
				//IJ.log(""+(0+c));
			}
		}
		if (count>0) {
			text = new String(chars);
			ta.setText(text);
		}
		if (count>0)
			IJ.showMessage("Zap Gremlins", count+" invalid characters converted to spaces");
		else
			IJ.showMessage("Zap Gremlins", "No invalid characters found");
	}

	void selectAll() {
		ta.selectAll();
	}

	void convertToPlugin() {
		if (!(getTitle().endsWith(".txt")||getTitle().endsWith(".ijm"))) return;
		String text = ta.getText();
		if (text==null || text.equals("")) {
			IJ.runPlugIn("ij.plugin.NewPlugin", " ");
			return;
		}
		if (text.indexOf("{")>-1) {
			IJ.showMessage("Convert to Plugin", "Conversion limited to recorder generated macro code.");
			return;
		}
		StringTokenizer st = new StringTokenizer(text, "\n");
		int n = st.countTokens();
		String line;
		StringBuffer sb = new StringBuffer();
		for(int i=0; i<n; i++) {
			line = st.nextToken();
			if (line!=null && line.length()>3) {
				if (line.equals("close();")) line = "run(\"Close\");";
				sb.append("\t\tIJ.");
				if (line.startsWith("//run"))
					line = line.substring(2);
				sb.append(line);
				sb.append('\n');
			}
		}
		NewPlugin np = (NewPlugin)IJ.runPlugIn("ij.plugin.NewPlugin", new String(sb));
		Editor ed = np.getEditor();
		String title = getTitle();
		if (title.equals("Macro.txt")||title.equals("Macro.ijm"))
			title = "Converted_Macro";
		if (title.endsWith(".txt")||title.endsWith(".ijm")) title = title.substring(0, title.length()-4);
		if (title.indexOf('_')==-1) title += "_";
		title += ".java";
		ed.updateClassName(ed.getTitle(), title);
		ed.setTitle(title);
	}
    
    void changeFontSize(boolean larger) {
        int in = fontSize;
        if (larger) {
            fontSize++;
            if (fontSize==sizes.length)
                fontSize = sizes.length-1;
        } else {
            fontSize--;
            if (fontSize<0)
                fontSize = 0;
        }
        IJ.showStatus(sizes[fontSize]+" point");
        setFont();
    }
    
    void saveSettings() {
		Prefs.set(FONT_SIZE, fontSize);
		Prefs.set(FONT_MONO, monospaced.getState());
		IJ.showStatus("Font settings saved (size="+sizes[fontSize]+", monospaced="+monospaced.getState()+")");
    }
    
    void setFont() {
        ta.setFont(new Font(getFontName(), Font.PLAIN, sizes[fontSize]));
    }
    
    String getFontName() {
    	return monospaced.getState()?"Monospaced":"SansSerif";
    }
	
	public void setFont(Font font) {
		ta.setFont(font);
	}

	public void append(String s) {
		ta.append(s);
	}

	public void setIsMacroWindow(boolean mw) {
		isMacroWindow = mw;
	}

	public static void setDefaultDirectory(String defaultDirectory) {
		defaultDir = defaultDirectory;
	}
	
	//public void keyReleased(KeyEvent e) {}
	//public void keyTyped(KeyEvent e) {}
	public void lostOwnership (Clipboard clip, Transferable cont) {}

}
