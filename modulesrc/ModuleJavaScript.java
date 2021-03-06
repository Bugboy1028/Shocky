import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.pircbotx.Channel;
import org.pircbotx.PircBotX;
import org.pircbotx.User;

import com.sun.script.javascript.RhinoScriptEngine;

import pl.shockah.StringTools;
import pl.shockah.shocky.ScriptModule;
import pl.shockah.shocky.Utils;
import pl.shockah.shocky.cmds.Command;
import pl.shockah.shocky.cmds.CommandCallback;
import pl.shockah.shocky.cmds.Parameters;
import pl.shockah.shocky.cmds.Command.EType;
import pl.shockah.shocky.sql.Factoid;
import pl.shockah.shocky.threads.SandboxThreadFactory;
import pl.shockah.shocky.threads.SandboxThreadGroup;

public class ModuleJavaScript extends ScriptModule {
	protected Command cmd;
	private final SandboxThreadGroup sandboxGroup = new SandboxThreadGroup("javascript");
	private final ThreadFactory sandboxFactory = new SandboxThreadFactory(sandboxGroup);

	public String name() {return "javascript";}
	public String identifier() {return "js";}
	public void onEnable(File dir) {
		Command.addCommands(this, cmd = new CmdJavascript());
		Command.addCommand(this, "js",cmd);
		
		try {
			Class.forName("ModuleJavaScript$Sandbox");
			Class.forName("ModuleJavaScript$JSRunner");
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}
	public void onDisable() {
		Command.removeCommands(cmd);
	}

	public synchronized String parse(Map<Integer,Object> cache, final PircBotX bot, EType type, Channel channel, User sender, Factoid factoid, String code, String message) {
		if (code == null) return "";
		
		RhinoScriptEngine engine = new RhinoScriptEngine();
		
		if (message != null) {
			String[] args = message.split(" ");
			String argsImp = StringTools.implode(args,1," "); if (argsImp == null) argsImp = "";
			engine.put("argc",(args.length-1));
			engine.put("args",argsImp);
			engine.put("ioru",(args.length-1 == 0 ? sender.getNick() : argsImp));
			engine.put("arg",Arrays.copyOfRange(args, 1, args.length));
		}
		
		engine.put("channel", channel.getName());
		engine.put("bot", bot.getNick());
		engine.put("sender", sender.getNick());
		
		Sandbox sandbox = new Sandbox(channel.getUsers().toArray(new User[0]));
		engine.put("bot", sandbox);

		JSRunner r = new JSRunner(engine, code);

		final ExecutorService service = Executors.newSingleThreadExecutor(sandboxFactory);
		TimeUnit unit = TimeUnit.SECONDS;
		String output = null;
		try {
			Future<String> f = service.submit(r);
		    output = f.get(30, unit);
		}
		catch(TimeoutException e) {
		    output = "Script timed out";
		}
		catch(Exception e) {
			e.printStackTrace();
			output = e.getMessage();
		}
		finally {
		    service.shutdown();
		}
		if (output == null || output.isEmpty())
			return null;

		return output;
	}

	public class CmdJavascript extends Command {
		public String command() {return "javascript";}
		public String help(Parameters params) {
			return "javascript/js\njavascript {code} - runs JavaScript code";
		}

		public void doCommand(Parameters params, CommandCallback callback) {
			if (params.tokenCount < 1) {
				callback.type = EType.Notice;
				callback.append(help(params));
				return;
			}
			
			String output = parse(null,params.bot,params.type,params.channel,params.sender,null,params.input,null);
			if (output != null && !output.isEmpty())
				callback.append(StringTools.limitLength(StringTools.formatLines(output)));
		}
	}
	
	public class Sandbox {
		private Random rnd = new Random();
		private final User[] users;
		
		public Sandbox(User[] users) {
			this.users = users;
		}
		
		public String randnick() {
			return users[rnd.nextInt(users.length)].getNick();
		}
		
		public String format(String format, Object... args) {
			return String.format(format, args);
		}
		
		public String munge(String in) {
			return Utils.mungeNick(in);
		}
		
		public String odd(String in) {
			return Utils.odd(in);
		}
		
		public String flip(String in) {
			return Utils.flip(in);
		}
		
		public String reverse(String in) {
			return new StringBuilder(in).reverse().toString();
		}
		
		public String toString() {
			return "Yes it is a bot";
		}
	}
	
	public class JSRunner implements Callable<String> {
		
		private final ScriptEngine engine;
		private final String code;
		
		public JSRunner(ScriptEngine e, String c) {
			engine = e;
			code = c;
		}

		@Override
		public String call() throws Exception {
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				ScriptContext context = engine.getContext();
				context.setWriter(pw);
				context.setErrorWriter(pw);
				
				try {
					Object out = engine.eval(code);
					if (sw.getBuffer().length() != 0)
						return sw.toString();
					if (out != null)
						return out.toString();
				}
				catch(ScriptException ex) {
					return ex.getMessage();
				}
				return null;
		}
		
	}
}