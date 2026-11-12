using System;
using System.Threading;
using Windows.Devices.Bluetooth.Advertisement;

public static class SinusBle
{
    private static readonly object Gate = new object();

    private static void Line(string text)
    {
        lock (Gate)
        {
            Console.Out.WriteLine(text);
            Console.Out.Flush();
        }
    }

    public static int Main(string[] args)
    {
        ushort company = 0xFFFF;
        if (args.Length > 0) company = Convert.ToUInt16(args[0], 16);
        var watchdog = new Thread(() =>
        {
            try { while (Console.In.Read() != -1) { } } catch (Exception) { }
            Environment.Exit(0);
        });
        watchdog.IsBackground = true;
        watchdog.Start();

        var w = new BluetoothLEAdvertisementWatcher();
        w.ScanningMode = BluetoothLEScanningMode.Passive;
        w.SignalStrengthFilter.SamplingInterval = TimeSpan.FromMilliseconds(250);
        w.Received += (s, a) =>
        {
            foreach (var m in a.Advertisement.ManufacturerData)
            {
                if (m.CompanyId != company) continue;
                var reader = Windows.Storage.Streams.DataReader.FromBuffer(m.Data);
                var bytes = new byte[m.Data.Length];
                reader.ReadBytes(bytes);
                Line("A " + a.RawSignalStrengthInDBm + " " + BitConverter.ToString(bytes).Replace("-", "").ToLowerInvariant());
            }
        };
        w.Stopped += (s, a) =>
        {
            Line("E " + a.Error);
            Environment.Exit(3);
        };
        try
        {
            w.Start();
        }
        catch (Exception e)
        {
            Line("E " + e.HResult.ToString("X8"));
            return 3;
        }
        Line("S " + w.Status);
        Thread.Sleep(Timeout.Infinite);
        return 0;
    }
}
