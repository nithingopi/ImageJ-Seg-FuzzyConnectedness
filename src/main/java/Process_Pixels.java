import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import ij.IJ;
import ij.ImageJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GUI;
import ij.gui.ImageCanvas;
import ij.gui.ImageRoi;
import ij.gui.NewImage;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.measure.Calibration;
import ij.plugin.frame.PlugInFrame;
import ij.process.ImageProcessor;

public class Process_Pixels extends PlugInFrame implements MouseListener, ImageListener
{
	private static final long serialVersionUID = 1L;
	private Panel panel;
	private static Frame instance;
	private boolean _pickingSeeds = false;
	private float _opacity = 0.5f;
	
	private HashMap<ImagePlus, SegmentStack> _imageSegmentMap = new HashMap<ImagePlus, SegmentStack>();	
	
    public Process_Pixels() 
    {
		super("Process pixels");
		if (instance!=null) {
			instance.toFront();
			return;
		}		
		instance = this;
		addKeyListener(IJ.getInstance());	
		ImagePlus.addImageListener(this);
		
		updateSegmentDictionary();
		
		drawOverlay(WindowManager.getCurrentImage());
		
		createGUI();
	}
    
    /**
     * Connects a [0, 100] slider to a text field so that when one updates the other
     * follows. The displayed value will also be divided by 100, so that it seems we're
     * manipulating a [0, 1] number
     * @param slider A JSlider with min set to 0 and max set to 100
     * @param text A JTextField
     */
    public void bindSliderAndTextField(final JSlider slider, final JTextField text)
    {
    	slider.addChangeListener(new ChangeListener(){
            @Override
            public void stateChanged(ChangeEvent e) {
            	text.setText(String.valueOf((slider.getValue()) / 100.0));
            }
        });
    	text.addKeyListener(new KeyAdapter(){
            @Override
            public void keyReleased(KeyEvent ke) {
            	String typed = text.getText();
            	slider.setValue(0);
                if(!typed.matches("\\d+(\\.\\d*)?")) {
                    return;
                }
                double value = Double.parseDouble(typed);
                slider.setValue((int)(value * 100.0));
            }
        });
    }

    public void createGUI()
    {    	
		setLayout(new FlowLayout());		
		
		//Create the GUI panel and add all the controls
		panel = new Panel();
		panel.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
				
		JLabel thresholdLabel = new JLabel("Object threshold");
		c.gridx = 0;
		c.gridy = 0;	
		c.fill = GridBagConstraints.NONE;
		panel.add(thresholdLabel, c);		
		
		final JSlider objThresholdSlider = new JSlider(SwingConstants.HORIZONTAL, 0, 100, 50);
		objThresholdSlider.setMajorTickSpacing(10);
		objThresholdSlider.setPaintTicks(true);
		c.gridx = 1;
		c.gridy = 0;
		panel.add(objThresholdSlider, c);	
		
		final JTextField objThresholdField = new JTextField(5);
		objThresholdField.setText("0.5");
		c.gridx = 2;
		c.gridy = 0;
		panel.add(objThresholdField, c);
		
		JLabel seedsLabel = new JLabel("Seeds");
		c.gridx = 0;
		c.gridy = 1;
		panel.add(seedsLabel, c);		
		
		final JToggleButton seedsButton = new JToggleButton("Select seeds");
		seedsButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				_pickingSeeds = !_pickingSeeds;
			}
		});
		seedsButton.addKeyListener(IJ.getInstance());
		c.gridx = 1;		
		c.gridy = 1;
		c.gridwidth = 1;
		panel.add(seedsButton, c);
		
		JButton seedsResetButton = new JButton("Reset seeds");
		seedsResetButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				clearSeeds();
			}
		});
		seedsResetButton.addKeyListener(IJ.getInstance());
		c.gridx = 2;		
		c.gridy = 1;
		c.gridwidth = 1;
		panel.add(seedsResetButton, c);
		
		JLabel outputFuzzyLabel = new JLabel("Output fuzzy image");
		c.gridx = 0;
		c.gridy = 2;
		c.gridwidth = 1;
		panel.add(outputFuzzyLabel, c);
		
		JCheckBox outputFuzzyCheckbox = new JCheckBox();
		c.gridx = 1;
		c.gridy = 2;
		c.gridwidth = 2;
		panel.add(outputFuzzyCheckbox, c);
				
		JLabel outputBinaryLabel = new JLabel("Output binary image");
		c.gridx = 0;
		c.gridy = 3;
		c.gridwidth = 1;
		panel.add(outputBinaryLabel, c);		
		
		JCheckBox outputBinaryCheckbox = new JCheckBox();
		c.gridx = 1;
		c.gridy = 3;		 
		c.gridwidth = 2;
		panel.add(outputBinaryCheckbox, c);		
		
		JLabel binaryThresholdLabel = new JLabel("Binarization threshold");	
		c.gridx = 0;
		c.gridy = 4;
		c.gridwidth = 1;
		panel.add(binaryThresholdLabel, c);				
		
		JSlider binaryThresholdSlider = new JSlider(SwingConstants.HORIZONTAL, 0, 100, 50);
		binaryThresholdSlider.setMajorTickSpacing(10);
		binaryThresholdSlider.setPaintTicks(true);
		c.gridx = 1;
		c.gridy = 4;
		panel.add(binaryThresholdSlider, c);
		
		JTextField binaryThresholdField = new JTextField(5);
		binaryThresholdField.setText("0.5");
		c.gridx = 2;
		c.gridy = 4;
		panel.add(binaryThresholdField, c);
				
		JLabel segmentOpacityLabel = new JLabel("Segment opacity");	
		c.gridx = 0;
		c.gridy = 5;
		c.gridwidth = 1;
		panel.add(segmentOpacityLabel, c);	
		
		final JSlider segmentOpacitySlider = new JSlider(SwingConstants.HORIZONTAL, 0, 100, 50);
		segmentOpacitySlider.setMajorTickSpacing(10);
		segmentOpacitySlider.setPaintTicks(true);
		segmentOpacitySlider.addChangeListener(new ChangeListener()
		{
            @Override
            public void stateChanged(ChangeEvent e) 
            {
            	_opacity = segmentOpacitySlider.getValue() / 100.0f;
            	ImagePlus img = WindowManager.getCurrentImage();
            	img.updateAndDraw();
            }
        });
		c.gridx = 1;
		c.gridy = 5;
		panel.add(segmentOpacitySlider, c);
		
		JTextField segmentOpacityField = new JTextField(5);
		segmentOpacityField.setText("0.5");
		c.gridx = 2;
		c.gridy = 5;
		panel.add(segmentOpacityField, c);
		
		//Create a bottom panel for the main execution controls
		Panel bottomPanel = new Panel();
		bottomPanel.setLayout(new FlowLayout());
		
		JButton runButton = new JButton("Run");
		runButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				runSegmentation();
			}
		});
		bottomPanel.add(runButton, c);		
		
		JButton clearButton = new JButton("Clear");
		clearButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				clearSegment();
			}
		});
		bottomPanel.add(clearButton, c);	
		
		JButton resetButton = new JButton("Reset parameters");
		bottomPanel.add(resetButton, c);			
		
		//Color the bottom panel differently do highlight it
		panel.setBackground(binaryThresholdSlider.getBackground());
		bottomPanel.setBackground(Color.white);
		
		//Add bottom panel at the bottom of the main parameter panel
		c.gridwidth = 3;
		c.gridheight = 1;
		c.gridx = 0;
		c.gridy = 6;
		c.fill = GridBagConstraints.BOTH;
		panel.add(bottomPanel, c);
		
		//Add the main parameter panel to our window
		add(panel);		
		
		pack();
		GUI.center(this);
		setVisible(true);		
		
		//Connect the slider and text fields so when one updates, so does the other
		bindSliderAndTextField(objThresholdSlider, objThresholdField);
		bindSliderAndTextField(binaryThresholdSlider, binaryThresholdField);
		bindSliderAndTextField(segmentOpacitySlider, segmentOpacityField);
    }
    
    public void updateSegmentDictionary()
    {    	
    	//Get the current image and set it into _currentImage
    	String[] titles = WindowManager.getImageTitles();    	
    	int numTitles = titles.length; 
    	
    	//Delete segments without a corresponding ImagePlus (has been deleted)
    	for(Iterator<HashMap.Entry<ImagePlus, SegmentStack>> it = _imageSegmentMap.entrySet().iterator(); it.hasNext(); ) 
    	{
    	    HashMap.Entry<ImagePlus, SegmentStack> entry = it.next();
    	      
    	    String title = entry.getKey().getTitle();    	    
    	    boolean contained = false;
    	    
    	    for(int i = 0; i < numTitles; i++)
    	    {
    	    	if(titles[i].equals(title))
    	    		contained = true;
    	    }
    	      
    	    if(!contained)
    	    {
    	    	System.out.println("Removing " + title + "'s segment");
    	        it.remove();
    	    }
	    }  	
    	
    	//Add a segment for each active ImagePlus
    	for(int i = 0; i < numTitles; i++)
    	{
    		ImagePlus img = WindowManager.getImage(titles[i]);    		
    		
    		img.getCanvas().removeMouseListener(this);    		
    		img.getCanvas().addMouseListener(this);
    		
    		if(!_imageSegmentMap.containsKey(img))
    		{
    			SegmentStack seg = new SegmentStack();
    			
    			ImageStack stack = img.getStack();
    			
    			seg.width = stack.getWidth();
    			seg.height = stack.getHeight();
    			seg.depth = stack.getSize();    			    			        			
    			seg._stack = new short[seg.width * seg.height * seg.depth];
    			
    			_imageSegmentMap.putIfAbsent(img, seg);
    			
    			System.out.println("New segment tied to image \"" + img.getTitle() + "\", with size " + stack.getWidth() + ", " + stack.getHeight() + ", " + stack.getSize());
    		}
    	}
    	
    	System.out.println("Current number of segments: " + _imageSegmentMap.size());
    }
    
    public void drawOverlay(ImagePlus imp)
    {       			
    	SegmentStack segStack = _imageSegmentMap.get(imp);    	
				
    	int currIndex = imp.getCurrentSlice() - 1;
    	
    	//Create the segment image
    	ImagePlus segImp = NewImage.createShortImage(imp.getTitle() + "_SEG", segStack.width, segStack.height, 1, 0);    	
    	ImageProcessor ip = segImp.getProcessor();
    	
    	int start = currIndex * (segStack.width * segStack.height);
    	int end = start + segStack.width * segStack.height;
    	short[] pix = Arrays.copyOfRange(segStack._stack, start, end);    	
    	ip.setPixels(pix);    	
    	
    	//Paint seeds full white
    	Overlay overlay = new Overlay();    	
    	for(int i = 0; i < segStack.seeds.size(); i++)
    	{
    		Point3D seed = segStack.seeds.get(i);    		
    		
    		if(seed.z != currIndex) continue;
    		
    		PointRoi a = new PointRoi(seed.x, seed.y);    		
    		overlay.add(a);
    	}	
    	
		ImageRoi roy = new ImageRoi(0, 0, ip);
		roy.setZeroTransparent(true);			
		//roy.setOpacity(_opacity);
		roy.setOpacity(0.8);
		overlay.add(roy);
				
		imp.setOverlay(overlay);			
		imp.show();				
    }
    
    public void clearSeeds()
    {
    	ImagePlus img = WindowManager.getCurrentImage();
    	
    	if(!_imageSegmentMap.containsKey(img))
    		return;
    	
    	SegmentStack seg = _imageSegmentMap.get(img); 
    	seg.seeds.clear();
    	
    	img.updateAndDraw();
    }
    
    public void clearSegment()
    {
    	ImagePlus img = WindowManager.getCurrentImage();
    	
    	if(!_imageSegmentMap.containsKey(img))
    		return;
    	
    	SegmentStack seg = _imageSegmentMap.get(img); 
    	seg._stack = new short[seg.width * seg.height * seg.depth];
    	
    	img.updateAndDraw();
    }
    
    public void runSegmentation()
    {    	
    	DialCache dial = new DialCache();    	
    	
    	ImagePlus img = WindowManager.getCurrentImage();
    	    	
    	System.out.println("Channels: " + img.getNChannels() + ", Bit depth: " + img.getBitDepth());
    	    	
    	if(!_imageSegmentMap.containsKey(img) || img.getNChannels() > 1)
    		return;
    	
    	SegmentStack seg = _imageSegmentMap.get(img); 
    	short[] segStack = new short[seg.width * seg.height * seg.depth];
    	seg._stack = segStack;    	    	
    	
    	ImageStack stack = img.getStack();
    	    	
    	int numSlices = stack.getSize();
    	int pixelsPerSlice = stack.getWidth() * stack.getHeight(); 
    	short[][] imagePixels = new short[numSlices][];
    	
    	//Grab references to the pixels of the entire stack
    	for(int i = 0; i < numSlices; i++)
    	{    		
    		imagePixels[i] = (short[]) stack.getProcessor(i + 1).getPixels();    		
    	}
    	
    	//Threshold from 0 to 1
    	short threshold = (short)(2.0 * (_opacity - 0.5) * 5000);   	
    	    	 	
    	double[] coeffs = img.getCalibration().getCoefficients();
    	double a = coeffs[1];
    	double b = coeffs[0];
    	
    	System.out.println("Thresholding with " + threshold);  
    	
    	//Convert the threshold to the same raw space that the image is in
    	threshold = (short)((threshold - b) / a);
    	
    	System.out.println("Thresholding with raw " + threshold);    	
    	
    	for(int i = 0; i < numSlices; i++)
    	{
    		short[] slice = imagePixels[i];
    		for(int j = 0; j < pixelsPerSlice; j++)
    		{    			 			
    			short val = slice[j];
    			
        		if(val > threshold)
        			segStack[i * pixelsPerSlice + j] = 0;
        		else
        			segStack[i * pixelsPerSlice + j] = Short.MIN_VALUE; 
    		}
    	}
    	
    	img.updateAndDraw();
    }
    
	public void processWindowEvent(WindowEvent e) 
	{
		super.processWindowEvent(e);
		if (e.getID()==WindowEvent.WINDOW_CLOSING) 
			instance = null;
	}

	public void mousePressed(MouseEvent e) 
	{
		if(_pickingSeeds)
		{							
			ImagePlus img = WindowManager.getCurrentImage();
			
			int x = e.getX();
			int y = e.getY();
			int z = img.getCurrentSlice() - 1; //Returns a one-based index
			
			//Compensate for image manipulation
			ImageCanvas canvas = img.getCanvas();
			x = canvas.offScreenX(x);
			y = canvas.offScreenY(y);			
			
			Point3D newPt = new Point3D(x, y, z);
			
			int[] a = img.getPixel(x, y);						
			Calibration b = img.getCalibration();			
			double[] coeffs = b.getCoefficients();
			
			short finalvalue = (short)(a[0] * coeffs[1] + coeffs[0]);
			
			System.out.println("Value: " + finalvalue);
			
			SegmentStack seg = _imageSegmentMap.get(img);			
			seg.seeds.add(newPt);			
			
			img.updateAndDraw();			
		}		
	}
	public void mouseClicked(MouseEvent e) {}
	public void mouseReleased(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	public void imageOpened(ImagePlus imp) 
	{	
		updateSegmentDictionary();
		
		drawOverlay(imp);
	}
	public void imageClosed(ImagePlus imp) 
	{	
		updateSegmentDictionary();
	}
	public void imageUpdated(ImagePlus imp) 
	{		
		//When we first open an image, IJ itself setting its title triggers imageUpdated.
		//If we run drawOverlay then, we'll crash, so we catch for these cases below		
		if(!_imageSegmentMap.containsKey(imp))
			return;
		
		drawOverlay(imp);
	}
	
	public static void main(String[] args) 
	{
		// set the plugins.dir property to make the plugin appear in the Plugins menu
		Class<?> clazz = Process_Pixels.class;
		String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
		String pluginsDir = url.substring("file:".length(), url.length() - clazz.getName().length() - ".class".length());
		System.setProperty("plugins.dir", pluginsDir);

		// start ImageJ
		new ImageJ();

		// open the Clown sample
		ImagePlus image = IJ.openImage("C:\\Users\\Daniel\\Dropbox\\DICOM series\\ct_head_ex\\CTHd058");
		image.show();

		// run the plugin
		IJ.runPlugIn(clazz.getName(), "");		
	}
}
