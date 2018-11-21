import java.awt.Color;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class PPTableEditorGUI extends JFrame
{
    public static void main(String[] args)
    {
        new PPTableEditorGUI();
    }

    public PPTableEditorGUI()
    {
        super("PP Table Editor");

        main_panel.setLayout(new FlowLayout());

        add_menu_bar();

        setSize(300, 200);
        setResizable(false);
        setVisible(true);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        // open in center of screen
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice defaultScreen = ge.getDefaultScreenDevice();
        Rectangle rect = defaultScreen.getDefaultConfiguration().getBounds();
        int x = (int)(rect.getMaxX() - getWidth()) / 2;
        int y = (int)(rect.getMaxY() - getHeight()) / 2;
        setLocation(x, y);
    }

    private void add_menu_bar()
    {
        JMenuBar menu_bar = new JMenuBar();

        JMenu menu_file = new JMenu("File");
        menu_bar.add(menu_file);

        JMenuItem menu_item_open = new JMenuItem("Open");
        menu_file.add(menu_item_open);

        JMenuItem menu_item_saveas = new JMenuItem("Save As");
        menu_file.add(menu_item_saveas);

        ActionListener listener = new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                JFileChooser fc = new JFileChooser();

                // start in current directory
                Path cd = Paths.get("");
                fc.setCurrentDirectory(cd.toAbsolutePath().toFile());

                if(e.getSource() == menu_item_open)
                {
                    if(fc.showOpenDialog(main_panel) == JFileChooser.APPROVE_OPTION)
                    {
                        try
                        {
                            File file = fc.getSelectedFile();
                            ppte = new PPTableEditor(file.getAbsolutePath());

                            SwingUtilities.invokeLater(new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    if(panel_core != null)
                                        main_panel.remove(panel_core);

                                    if(panel_voltage != null)
                                        main_panel.remove(panel_voltage);

                                    if(panel_memory != null)
                                        main_panel.remove(panel_memory);

                                    if(panel_power != null)
                                        main_panel.remove(panel_power);

                                    add_core_panel();
                                    add_voltage_panel();
                                    add_memory_panel();
                                    add_power_panel();

                                    revalidate();
                                    repaint();
                                    pack();
                                }
                            });
                        }
                        catch(IllegalArgumentException ex)
                        {
                            show_error_dialog(ex.getMessage());
                        }
                    }
                }
                else if(e.getSource() == menu_item_saveas)
                {
                    if(ppte == null)
                    {
                        show_error_dialog("Please open a PowerPlay registry file first");
                        return;
                    }

                    if(fc.showSaveDialog(main_panel) == JFileChooser.APPROVE_OPTION)
                    {
                        String save_path = fc.getSelectedFile().getAbsolutePath();

                        if(ppte.save(save_path))
                            show_success_dialog("Successfully saved to " + save_path);
                        else show_error_dialog("Failed to save PowerPlay registry file");
                    }
                }
            }
        };

        menu_item_open.addActionListener(listener);
        menu_item_saveas.addActionListener(listener);

        setJMenuBar(menu_bar);
    }

    private void add_core_panel()
    {
        panel_core = new JPanel();
        BoxLayout layout = new BoxLayout(panel_core, BoxLayout.Y_AXIS);
        panel_core.setLayout(layout);

        for(PPTableEditor.ATOM_SCLK_ENTRY e : ppte.sclk_entries)
        {
            JPanel row = new JPanel();

            JTextField txt_clock = new JTextField(5);
            txt_clock.setText(String.valueOf(e.ulSclk / 100));
            row.add(txt_clock);
            JTextField txt_index = new JTextField(2);
            txt_index.setText(String.valueOf(e.ucVddInd));
            row.add(txt_index);

            DocumentListener listener = new DocumentListener()
            {
                @Override
                public void removeUpdate(DocumentEvent ev)
                {
                    changedUpdate(ev);
                }
            
                @Override
                public void insertUpdate(DocumentEvent ev)
                {
                    changedUpdate(ev);
                }
            
                @Override
                public void changedUpdate(DocumentEvent ev)
                {
                    if(ev.getDocument() == txt_clock.getDocument())
                    {
                        txt_clock.setBackground(Color.WHITE);
                        String s = txt_clock.getText();
                        if(s.isEmpty()) return;

                        try {
                            e.ulSclk = Integer.parseInt(s) * 100;
                        }
                        catch(NumberFormatException ex)
                        {
                            txt_clock.setBackground(invalid);
                        }
                    }
                    else if(ev.getDocument() == txt_index.getDocument())
                    {
                        txt_index.setBackground(Color.WHITE);
                        String s = txt_index.getText();
                        if(s.isEmpty()) return;

                        try {
                            e.ucVddInd = (byte)Integer.parseInt(s);
                        }
                        catch(NumberFormatException ex)
                        {
                            txt_index.setBackground(invalid);
                        }
                    }
                }
            };

            txt_clock.getDocument().addDocumentListener(listener);
            txt_index.getDocument().addDocumentListener(listener);

            panel_core.add(row);
        }

        panel_core.setBorder(BorderFactory.createTitledBorder("Core"));

        main_panel.add(panel_core);
    }

    private void add_voltage_panel()
    {
        panel_voltage = new JPanel();
        BoxLayout layout = new BoxLayout(panel_voltage, BoxLayout.Y_AXIS);
        panel_voltage.setLayout(layout);

        for(int i = 0; i < ppte.voltage_entries.length; i++)
        {
            PPTableEditor.ATOM_VOLTAGE_ENTRY entry = ppte.voltage_entries[i];
            JPanel row = new JPanel();

            JTextField txt_index = new JTextField(2);
            txt_index.setText(String.valueOf(i));
            txt_index.setEditable(false);
            row.add(txt_index);
            JTextField txt_voltage = new JTextField(5);
            txt_voltage.setText(String.valueOf(entry.usVdd));
            row.add(txt_voltage);

            txt_voltage.getDocument().addDocumentListener(new DocumentListener()
            {
                @Override
                public void removeUpdate(DocumentEvent e)
                {
                    changedUpdate(e);
                }
            
                @Override
                public void insertUpdate(DocumentEvent e)
                {
                    changedUpdate(e);
                }
            
                @Override
                public void changedUpdate(DocumentEvent e)
                {
                    txt_voltage.setBackground(Color.WHITE);
                    String s = txt_voltage.getText();
                    if(s.isEmpty()) return;

                    try {
                        entry.usVdd = Integer.parseInt(s);
                    }
                    catch(NumberFormatException ex)
                    {
                        txt_voltage.setBackground(invalid);
                    }
                }
            });

            panel_voltage.add(row);
        }

        panel_voltage.setBorder(BorderFactory.createTitledBorder("Voltage"));

        main_panel.add(panel_voltage);
    }

    private void add_memory_panel()
    {
        panel_memory = new JPanel();
        BoxLayout layout = new BoxLayout(panel_memory, BoxLayout.Y_AXIS);
        panel_memory.setLayout(layout);

        for(PPTableEditor.ATOM_MCLK_ENTRY e : ppte.mclk_entries)
        {
            JPanel row = new JPanel();

            JTextField txt_clock = new JTextField(5);
            txt_clock.setText(String.valueOf(e.ulMclk / 100));
            row.add(txt_clock);
            JTextField txt_voltage = new JTextField(5);
            txt_voltage.setText(String.valueOf(e.usMvdd));
            row.add(txt_voltage);

            DocumentListener listener = new DocumentListener()
            {
                @Override
                public void removeUpdate(DocumentEvent ev)
                {
                    changedUpdate(ev);
                }
            
                @Override
                public void insertUpdate(DocumentEvent ev)
                {
                    changedUpdate(ev);
                }
            
                @Override
                public void changedUpdate(DocumentEvent ev)
                {
                    if(ev.getDocument() == txt_clock.getDocument())
                    {
                        txt_clock.setBackground(Color.WHITE);
                        String s = txt_clock.getText();
                        if(s.isEmpty()) return;

                        try {
                            e.ulMclk = Integer.parseInt(s) * 100;
                        }
                        catch(NumberFormatException ex)
                        {
                            txt_clock.setBackground(invalid);
                        }
                    }
                    else if(ev.getDocument() == txt_voltage.getDocument())
                    {
                        txt_voltage.setBackground(Color.WHITE);
                        String s = txt_voltage.getText();
                        if(s.isEmpty()) return;

                        try {
                            e.usMvdd = Integer.parseInt(s);
                        }
                        catch(NumberFormatException ex)
                        {
                            txt_voltage.setBackground(invalid);
                        }
                    }
                }
            };

            txt_clock.getDocument().addDocumentListener(listener);
            txt_voltage.getDocument().addDocumentListener(listener);

            panel_memory.add(row);
        }

        panel_memory.setBorder(BorderFactory.createTitledBorder("Memory"));

        main_panel.add(panel_memory);
    }

    private void add_power_panel()
    {
        panel_power = new JPanel();
        BoxLayout layout = new BoxLayout(panel_power, BoxLayout.Y_AXIS);
        panel_power.setLayout(layout);

        JPanel panel_pplay = new JPanel();
        panel_pplay.add(new JLabel("Power limit (%):"));
        JTextField txt_power_limit = new JTextField(3);
        txt_power_limit.setText(String.valueOf(ppte.pplay.usPowerControlLimit));
        panel_pplay.add(txt_power_limit);
        panel_power.add(panel_pplay);

        txt_power_limit.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void removeUpdate(DocumentEvent e)
            {
                changedUpdate(e);
            }
        
            @Override
            public void insertUpdate(DocumentEvent e)
            {
                changedUpdate(e);
            }
        
            @Override
            public void changedUpdate(DocumentEvent e)
            {
                txt_power_limit.setBackground(Color.WHITE);
                String s = txt_power_limit.getText();
                if(s.isEmpty()) return;

                try {
                    ppte.pplay.usPowerControlLimit = Integer.parseInt(s);
                }
                catch(NumberFormatException ex)
                {
                    txt_power_limit.setBackground(invalid);
                }
            }
        });

        JPanel p = new JPanel();
        p.add(new JLabel("TDP (W):"));
        JTextField txt_tdp = new JTextField(3);
        txt_tdp.setText(String.valueOf(ppte.ptune.usTDP));
        p.add(txt_tdp);
        panel_power.add(p);

        p = new JPanel();
        p.add(new JLabel("TDC (A):"));
        JTextField txt_tdc = new JTextField(3);
        txt_tdc.setText(String.valueOf(ppte.ptune.usTDC));
        p.add(txt_tdc);
        panel_power.add(p);

        p = new JPanel();
        p.add(new JLabel("Max power limit (W):"));
        JTextField txt_max_plimit = new JTextField(3);
        txt_max_plimit.setText(String.valueOf(ppte.ptune.usMaximumPowerDeliveryLimit));
        p.add(txt_max_plimit);
        panel_power.add(p);

        DocumentListener listener = new DocumentListener()
        {
            @Override
            public void removeUpdate(DocumentEvent e)
            {
                changedUpdate(e);
            }
        
            @Override
            public void insertUpdate(DocumentEvent e)
            {
                changedUpdate(e);
            }
        
            @Override
            public void changedUpdate(DocumentEvent e)
            {
                if(e.getDocument() == txt_tdp.getDocument())
                {
                    txt_tdp.setBackground(Color.WHITE);
                    String s = txt_tdp.getText();
                    if(s.isEmpty()) return;

                    try {
                        ppte.ptune.usTDP = Integer.parseInt(s);
                    }
                    catch(NumberFormatException ex)
                    {
                        txt_tdp.setBackground(invalid);
                    }
                }
                else if(e.getDocument() == txt_tdc.getDocument())
                {
                    txt_tdc.setBackground(Color.WHITE);
                    String s = txt_tdc.getText();
                    if(s.isEmpty()) return;

                    try {
                        ppte.ptune.usTDC = Integer.parseInt(s);
                    }
                    catch(NumberFormatException ex)
                    {
                        txt_tdc.setBackground(invalid);
                    }
                }
                else if(e.getDocument() == txt_max_plimit.getDocument())
                {
                    txt_max_plimit.setBackground(Color.WHITE);
                    String s = txt_max_plimit.getText();
                    if(s.isEmpty()) return;

                    try {
                        ppte.ptune.usMaximumPowerDeliveryLimit = Integer.parseInt(s);
                    }
                    catch(NumberFormatException ex)
                    {
                        txt_max_plimit.setBackground(invalid);
                    }
                }
            }
        };
        txt_tdp.getDocument().addDocumentListener(listener);
        txt_tdc.getDocument().addDocumentListener(listener);
        txt_max_plimit.getDocument().addDocumentListener(listener);

        panel_power.setBorder(BorderFactory.createTitledBorder("Power Limits"));

        main_panel.add(panel_power);
    }

    private void show_error_dialog(String msg)
    {
        JOptionPane.showMessageDialog(
            main_panel,
            msg,
            "Error",
            JOptionPane.ERROR_MESSAGE
        );
    }

    private void show_success_dialog(String msg)
    {
        JOptionPane.showMessageDialog(
            main_panel,
            msg,
            "Success",
            JOptionPane.INFORMATION_MESSAGE
        );
    }

    private Container main_panel = getContentPane();
    private JPanel panel_core, panel_voltage, panel_memory, panel_power;
    private PPTableEditor ppte;
    private final Color invalid = new Color(0xFFFFAFAF);
}