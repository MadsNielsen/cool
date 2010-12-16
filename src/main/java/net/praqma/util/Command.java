package net.praqma.util;


import java.io.File;
import java.io.IOException;

import net.praqma.util.Debug;

/**
 * CLI class
 * @author wolfgang
 *
 */
public abstract class Command
{
	protected static Debug logger = Debug.GetLogger();
	protected static final String linesep = System.getProperty( "line.separator" );
	
	public static CmdResult run( String cmd ) throws CommandLineException, AbnormalProcessTerminationException
	{
		return run( cmd, null, false );
	}
	
	public static CmdResult run( String cmd, File dir ) throws CommandLineException, AbnormalProcessTerminationException
	{
		return run( cmd, dir, false );
	}
	
//	public static CmdResult run( String cmd, File dir ) throws CommandLineException, AbnormalProcessTerminationException
//	{
//		String[] cmds = new String[3];
//		cmds[0] = "cmd.exe";
//		cmds[1] = "/C";
//		cmds[2] = cmd;
//		
//		//String[] cmds = {cmd};
//		return run( cmds, dir );
//	}
	
	public static CmdResult run( String cmd, File dir, boolean merge ) throws CommandLineException, AbnormalProcessTerminationException
	{
		logger.trace_function();
		
		//cmd += ( merge ? " 2>&1" : "" );

		String[] cmds = new String[3];
		cmds[0] = "cmd.exe";
		cmds[1] = "/C";
		cmds[2] = cmd;
		
		logger.debug( "$ " + cmd );
		
		try
		{
			ProcessBuilder pb = new ProcessBuilder( cmds );
			pb.redirectErrorStream( merge );
			
			if( dir != null )
			{
				logger.debug( "Changing current working directory to " + dir );
				pb.directory( dir );
			}
			
			CmdResult result = new CmdResult();
			
			Process p = pb.start();

			/* Starting Gobbler threads */
			StreamGobbler output = new StreamGobbler( p.getInputStream() );
			StreamGobbler errors = new StreamGobbler( p.getErrorStream() );
			p.getOutputStream().close();

			output.start();
			errors.start();
			
			int exitValue = 0;
			try
			{
				exitValue = p.waitFor();
			}
			catch( InterruptedException e )
			{
				p.destroy();
			}
            finally
            {
                Thread.interrupted();
            } 
			
			/* Abnormal process termination, with error out as message */
			if ( exitValue != 0 )
			{
				logger.debug( "Abnormal process termination: " + errors.sres.toString() );
				throw new AbnormalProcessTerminationException( errors.sres.toString() );
			}
			
			try
			{
				output.join();
			}
			catch ( InterruptedException e )
			{
				logger.error( "Could not join output thread" );
			}
			
			try
			{
				errors.join();
			}
			catch ( InterruptedException e )
			{
				logger.error( "Could not join errors thread" );
			}
			
			/* Closing streams */
			p.getErrorStream().close();
			p.getInputStream().close();
			
			/* Setting command result */
			result.stdoutBuffer = output.sres;
			result.stdoutList   = output.lres;
			
			result.errorBuffer  = errors.sres;
			result.errorList    = errors.lres;
			
			return result;
		}
		catch ( IOException e )
		{
			logger.warning( "Could not execute the command \"" + cmd + "\" correctly: " + e.getMessage() );
			throw new CommandLineException( "Could not execute the command \"" + cmd + "\" correctly: " + e.getMessage() );
		}
	}
}